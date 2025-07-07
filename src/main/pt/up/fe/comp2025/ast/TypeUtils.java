package pt.up.fe.comp2025.ast;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.optimization.OllirExprResult;
import pt.up.fe.comp2025.symboltable.JmmSymbolTable;
import pt.up.fe.comp2025.utils.ReportUtils;

import java.util.List;

import static pt.up.fe.comp2025.ast.Kind.*;

/**
 * Utility methods regarding types.
 */
public class TypeUtils {

    private final JmmSymbolTable table;
    private final List<Report> reports;

    public TypeUtils(SymbolTable table) {
        this(table, null);
    }

    public TypeUtils(SymbolTable table, List<Report> reports) {
        this.table = (JmmSymbolTable) table;
        this.reports = reports;
    }

    public static Type newIntType() {
        return new Type("int", false);
    }

    public static Type newBooleanType() {
        return new Type("boolean", false);
    }

    public static Type newVoidType() {
        return new Type("void", false);
    }

    public static Type newStringType() {
        return new Type("String", false);
    }

    // Generic array type []
    // a somewhat ambiguous representation, but works
    public static Type newArrayType() {
        return new Type("void", true);
    }

    // "Unknown" type, from imported method
    public static Type newUnknownType(String importedClass) {
        Type t = new Type("", false);
        t.putObject("unknown", importedClass);
        return t;
    }

    public boolean isTypeImported(Type type) {
        //todo: Exemption for string??
        return !isPrimitiveType(type) &&
                !type.getName().equals(table.getClassName());
    }

    // An unknown type occurs when a method from an
    // imported class is called; the return type is "unknown"
    // (an unknown type is also an imported type)
    public static boolean isTypeUnknown(Type type) {
        return type.hasAttribute("unknown");
    }

    public static boolean isVararg(Type type) {
        return type.hasAttribute("vararg");
    }

    public static boolean isPrimitiveType(Type type) {
        return type.getName().equals(newIntType().getName())
                || type.getName().equals(newBooleanType().getName())
                || type.getName().equals(newVoidType().getName())
                // Even though, in Java, a String isn't a primitive,
                // in Java-- it is treated as such since we can't extend it
                || type.getName().equals(newStringType().getName());
    }

    /**
     * Converts a Jmm Type node to a {@link Type}.
     */
    public static Type convertType(JmmNode typeNode) {

        String name = typeNode.get("name");

        boolean isArray = Boolean.parseBoolean(typeNode.get("isArray"));

        // technically also an array
        boolean isVarArg = Boolean.parseBoolean(typeNode.get("isVarArg"));

        Type type = new Type(name, isArray || isVarArg);

        // Flag as var arg
        if (isVarArg) {
            type.putObject("vararg", true);
        }

        return type;
    }

    /**
     * Gets the {@link Type} of an arbitrary expression.
     */
    public Type getExprType(JmmNode expr) {
        Kind k = Kind.fromString(expr.getKind());
        switch (k) {
            case BOOLEAN_LIT:
            case LOGIC_EXPR:
            case UNARY_EXPR:
                return newBooleanType();

            case INTEGER_LIT:
            case LENGTH_ACCESS:
                return newIntType();

            // New fix
            case BINARY_EXPR:
                String op = expr.get("op");
                if (op.equals("<") || op.equals(">")) {
                    return newBooleanType();
                } else return newIntType();

            case THIS_REF:
                return new Type(table.getClassName(), false);

            case PARENTHESIS:
                JmmNode exprNode = expr.getChildren(EXPR).getFirst();
                return getExprType(exprNode);

            case ARRAY_ACCESS:
                JmmNode arrayNode = expr.getChildren(EXPR).getFirst();
                Type arrayType = getExprType(arrayNode);
                return new Type(arrayType.getName(), false);

            case ARRAY_CREATION:
                JmmNode typeNode = expr.getChildren(TYPE).getFirst();
                Type type = convertType(typeNode);
                return new Type(type.getName(), true);

            case ARRAY_INIT:
                List<JmmNode> elements = expr.getChildren(EXPR);
                if (elements.isEmpty()) {
                    // Empty array init; type is arbitrary
                    return newArrayType();
                }
                JmmNode elementNode = elements.getFirst();
                Type elementType = getExprType(elementNode);
                return new Type(elementType.getName(), true);

            case OBJECT_CREATION:
                String name = expr.get("name");

                // String object (special case)
                if (name.equals("String"))
                    return newStringType();

                // Our own class
                if (name.equals(table.getClassName())) {
                    return new Type(name, false);
                }
                //todo: Check validity in as a semantics pass?
                for (String i : table.getImports()) {
                    if (name.equals(i)) {
                        // Imported class
                        return new Type(name, false);
                    }
                }
                // Undefined class
                return null;

            case VAR_REF_EXPR:
                String varName = expr.get("name");
                JmmNode method = getEnclosingMethod(expr);

                if (method != null) {
                    // If within a method, check params, then locals
                    String mname = method.get("name");
                    for (Symbol v : table.getParameters(mname)) {
                        if (v.getName().equals(varName)) {
                            return v.getType();
                        }
                    }
                    for (Symbol v : table.getLocalVariables(mname)) {
                        if (v.getName().equals(varName)) {
                            return v.getType();
                        }
                    }
                }

                // Then check fields
                for (Symbol f : table.getFields()) {
                    if (f.getName().equals(varName)) {
                        // Static method cannot reference a field
                        // todo: Move to a semantic pass?
                        if (method != null && table.isMethodStatic(method.get("name"))) {
                            String message = String.format(
                                    "Static method '%s' cannot reference field '%s'",
                                    method.get("name"),
                                    f.getName());
                            if (reports != null) reports.add(ReportUtils.buildWarnReport(
                                    Stage.SEMANTIC,
                                    expr,
                                    message)
                            );
                            return null;
                        }
                        return f.getType();
                    }
                }
                // No reference
                return null;

            case METHOD_CALL:
                JmmNode object = expr.getChildren(EXPR).getFirst();
                Type objectType = getExprType(object);
                Type classType = new Type(table.getClassName(), false);
                String methodName = expr.get("name");

                if (objectType == null) {
                    // Cannot recognize object type, thus
                    // it's not an object, but a class
                    for (String _import : table.getImports()) {
                        if (_import.equals(object.get("name"))) {
                            // Static method from imported class
                            String message = String.format(
                                    "Inferred static method '%s' from imported class '%s'",
                                    methodName,
                                    _import);
                            if (reports != null) reports.add(ReportUtils.buildWarnReport(
                                    Stage.SEMANTIC,
                                    expr,
                                    message)
                            );
                            return newUnknownType(_import);
                        }
                    }
                    return null;
                }

                if (objectType.equals(classType)) {
                    // Method defined in our class
                    if (table.getMethods().contains(methodName)) {
                        return table.getReturnType(methodName);
                    }
                    // Superclass exists; assume method comes from there
                    if (!table.getSuper().isEmpty()) {
                        String message = String.format(
                                "Inferred method '%s' from superclass '%s'",
                                methodName,
                                table.getSuper());
                        if (reports != null) reports.add(ReportUtils.buildWarnReport(
                                Stage.SEMANTIC,
                                expr,
                                message)
                        );
                        return newUnknownType(table.getSuper());
                    }
                }

                // Method comes from imported class
                for (String i : table.getImports()) {
                    if (objectType.getName().equals(i)) {
                        String message = String.format(
                                "Inferred method '%s' from imported class '%s'",
                                methodName,
                                i);
                        if (reports != null) reports.add(ReportUtils.buildWarnReport(
                                Stage.SEMANTIC,
                                expr,
                                message)
                        );
                        return newUnknownType(i);
                    }
                }
                // Invalid method
                return null;
        }
        throw new IllegalArgumentException("Node not an expression");
    }

    /**
     * Get method enclosing an expression; Return null if expr
     * is declared in a field, i.e. not enclosed by a method
     */
    // todo: Use a Visitor???
    public static JmmNode getEnclosingMethod(JmmNode node) {
        while (node != null) {
            if (METHOD_DECL.check(node)) {
                return node;
            }
            if (CLASS_DECL.check(node)) {
                // Reached class; no enclosing method
                return null;
            }
            node = node.getParent();
        }
        return null;
    }

    /**
     * Check whether a {@link Type} inherits from another.
     */
    public boolean inheritsType(Type t1, Type t2) {

        if (isPrimitiveType(t1) || isPrimitiveType(t2)) {
            // "Primitives" do not and cannot be inherited
            return false;
        }

        String className = table.getClassName();

        if (t1.getName().equals(className)) {
            // Class extends T2
            String superName = table.getSuper();
            return t2.getName().equals(superName);
        }

        if (isTypeImported(t1)) {
            // Type is imported
            if (!t1.getName().equals(t2.getName())) {
                // Warn about inference
                String message = String.format(
                        "Inferred imported type '%s' as a subclass of '%s'",
                        t1.getName(),
                        t2.getName());
                if (reports != null)
                    reports.add(Report.newWarn(Stage.SEMANTIC, 0, 0, message, null));
            }
            return true;
        }

        return false;
    }

    /**
     *
     */
    public boolean isAssignableAs(Type t1, Type t2) {

        if (isTypeUnknown(t1) || isTypeUnknown(t2))
            return true;

        if (inheritsType(t1, t2))
            return true;

        // Any array can be assigned an empty array
        if (t2.isArray() && t1.equals(newArrayType()))
            return true;

        return t1.equals(t2);
    }

    /**
     * Check if a VarRefExpr refers to a class field.
     */
    public boolean isField(JmmNode node) {

        if (!VAR_REF_EXPR.check(node))
            return false;

        String name = node.get("name");
        JmmNode method = TypeUtils.getEnclosingMethod(node);

        if (method != null) {
            String mname = method.get("name");

            // Param
            for (Symbol v : table.getParameters(mname)) {
                if (v.getName().equals(name))
                    return false;
            }
            // Local
            for (Symbol v : table.getLocalVariables(mname)) {
                if (v.getName().equals(name))
                    return false;
            }
        }

        for (Symbol f : table.getFields()) {
            if (f.getName().equals(name))
                return true;
        }

        return false;
    }
}
