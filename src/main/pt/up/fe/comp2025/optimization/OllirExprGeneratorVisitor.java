package pt.up.fe.comp2025.optimization;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2025.ast.Kind;
import pt.up.fe.comp2025.ast.TypeUtils;

import java.util.ArrayList;
import java.util.List;

import static pt.up.fe.comp2025.ast.Kind.*;

/**
 * Generates OLLIR code from JmmNodes that are expressions.
 */
public class OllirExprGeneratorVisitor extends AJmmVisitor<Void, OllirExprResult> {

    private static final String SPACE = " ";
    private static final String ASSIGN = ":=";
    private final String END_STMT = ";\n";
    private final String NL = "\n";

    private final SymbolTable table;

    private final TypeUtils types;
    private final OptUtils ollirTypes;

    public OllirExprGeneratorVisitor(SymbolTable table, OptUtils ollirTypes) {
        this.table = table;
        this.ollirTypes = ollirTypes;
        this.types = new TypeUtils(table);
    }

    @Override
    protected void buildVisitor() {
        addVisit(THIS_REF, this::visitThis);
        addVisit(PARENTHESIS, this::visitParenthesis);
        addVisit(INTEGER_LIT, this::visitIntegerLit);
        addVisit(BOOLEAN_LIT, this::visitBooleanLit);
        addVisit(LENGTH_ACCESS, this::visitLengthAccess);
        addVisit(BINARY_EXPR, this::visitBinExpr);
        addVisit(UNARY_EXPR, this::visitUnaryExpr);
        addVisit(LOGIC_EXPR, this::visitLogicExpr);
        addVisit(OBJECT_CREATION, this::visitObjectCreation);
        addVisit(ARRAY_CREATION, this::visitArrayCreation);
        addVisit(ARRAY_INIT, this::visitArrayInit);
        addVisit(ARRAY_ACCESS, this::visitArrayAccess);
        addVisit(METHOD_CALL, this::visitMethodCall);
        addVisit(VAR_REF_EXPR, this::visitVarRef);
//        setDefaultVisit(this::defaultVisit);
    }

    private OllirExprResult visitThis(JmmNode node, Void unused) {
        String code = "this" + ollirTypes.toOllirType(table.getClassName());
        return new OllirExprResult(code);
    }

    private OllirExprResult visitParenthesis(JmmNode node, Void unused) {
        return visit(node.getChild(0));
    }

    private OllirExprResult visitIntegerLit(JmmNode node, Void unused) {
        String value = node.get("value");
        String code = value + OptUtils.i32_t;
        return new OllirExprResult(code);
    }

    private OllirExprResult visitBooleanLit(JmmNode node, Void unused) {
        String value = node.get("value").equals("true") ? "1" : "0";
        String code = value + OptUtils.bool_t;
        return new OllirExprResult(code);
    }

    private OllirExprResult visitLengthAccess(JmmNode node, Void unused) {
        var expr = visit(node.getChild(0));

        StringBuilder computation = new StringBuilder();
        computation.append(expr.getComputation());

        // Length access always yields an integer
        String typeCode = OptUtils.i32_t;

        String code;
        StringBuilder lengthCode = new StringBuilder();

        lengthCode.append("arraylength(")
                .append(expr.getCode())
                .append(")")
                .append(typeCode);

        // Only use tmp var if needed
        if (ASSIGN_STMT.check(node.getParent()))
            code = lengthCode.toString();
        else {
            code = ollirTypes.nextTemp() + typeCode;

            computation.append(code)
                    .append(SPACE)
                    .append(ASSIGN)
                    .append(typeCode)
                    .append(SPACE)
                    .append(lengthCode)
                    .append(END_STMT);
        }

        return new OllirExprResult(code, computation);
    }

    private OllirExprResult visitBinExpr(JmmNode node, Void unused) {
        var lhs = visit(node.getChild(0));
        var rhs = visit(node.getChild(1));

        StringBuilder computation = new StringBuilder();
        computation.append(lhs.getComputation());
        computation.append(rhs.getComputation());

        Type retType = types.getExprType(node);
        String typeCode = ollirTypes.toOllirType(retType);

        StringBuilder exprCode = new StringBuilder()
                .append(lhs.getCode())
                .append(SPACE)
                .append(node.get("op"))
                .append(typeCode)
                .append(SPACE)
                .append(rhs.getCode());

        String code;

        if (ASSIGN_STMT.check(node.getParent()))
            code = exprCode.toString();
        else {
            code = ollirTypes.nextTemp() + typeCode;

            computation.append(code)
                    .append(SPACE)
                    .append(ASSIGN)
                    .append(typeCode)
                    .append(SPACE)
                    .append(exprCode)
                    .append(END_STMT);
        }

        return new OllirExprResult(code, computation);
    }

    private OllirExprResult visitUnaryExpr(JmmNode node, Void unused) {
        var expr = visit(node.getChild(0));

        StringBuilder computation = new StringBuilder();
        computation.append(expr.getComputation());

        // Unary expr is always of type bool
        String typeCode = OptUtils.bool_t;
        String code;

        StringBuilder exprCode = new StringBuilder()
                .append("!")
                .append(OptUtils.bool_t)
                .append(SPACE)
                .append(expr.getCode());

        if (ASSIGN_STMT.check(node.getParent()))
            code = exprCode.toString();
        else {
            code = ollirTypes.nextTemp() + typeCode;

            computation.append(code)
                    .append(SPACE)
                    .append(ASSIGN)
                    .append(typeCode)
                    .append(SPACE)
                    .append(exprCode)
                    .append(END_STMT);
        }

        return new OllirExprResult(code, computation);
    }

    private OllirExprResult visitLogicExpr(JmmNode node, Void unused) {
        StringBuilder computation = new StringBuilder();

        var lhs = visit(node.getChild(0));
        var rhs = visit(node.getChild(1));

        // Logic expr always yields a bool
        String typeCode = OptUtils.bool_t;
        String code = ollirTypes.nextTemp("andTmp") + typeCode;

        String thenLabel = ollirTypes.nextTemp("then");
        String endLabel = ollirTypes.nextTemp("endif");

        computation
        // A.computation
                .append(lhs.getComputation())
        // if A.code goto then
                .append("if (")
                .append(lhs.getCode())
                .append(") goto ")
                .append(thenLabel)
                .append(END_STMT)
        // andTmp = false
                .append(code)
                .append(SPACE)
                .append(ASSIGN)
                .append(typeCode)
                .append(SPACE)
                .append(0)
                .append(typeCode)
                .append(END_STMT)
        // goto endif
                .append("goto ")
                .append(endLabel)
                .append(END_STMT)
                .append(NL)
        // "Then label"
                .append(thenLabel)
                .append(":\n")
        // B.computation
                .append(rhs.getComputation())
        // andTmp = B.code
                .append(code)
                .append(SPACE)
                .append(ASSIGN)
                .append(typeCode)
                .append(SPACE)
                .append(rhs.getCode())
                .append(END_STMT)
        // "Endif" label
                .append(endLabel)
                .append(":\n");

        return new OllirExprResult(code, computation);
    }

    private OllirExprResult visitObjectCreation(JmmNode node, Void unused) {
        StringBuilder computation = new StringBuilder();

        String className = node.get("name");
        // Special case for String class instantiation
        if (className.equals("String"))
            className = "\"String\"";

        String typeCode = ollirTypes.toOllirType(className);
        String code = ollirTypes.nextTemp() + typeCode;

        computation
                .append(code)
                .append(SPACE)
                .append(ASSIGN)
                .append(typeCode)
                .append(SPACE)
                .append("new(")
                .append(className)
                .append(")")
                .append(typeCode)
                .append(END_STMT)
                //
                .append("invokespecial(")
                .append(code)
                .append(", \"<init>\").V")
                .append(END_STMT);

        return new OllirExprResult(code, computation);
    }

    private OllirExprResult visitArrayCreation(JmmNode node, Void unused) {
        var expr = visit(node.getChild(1));

        StringBuilder computation = new StringBuilder();
        computation.append(expr.getComputation());

        Type type = TypeUtils.convertType(node.getChild(0));
        String typeCode = ".array" + ollirTypes.toOllirType(type); // !

        StringBuilder arrayCode = new StringBuilder()
                .append("new(array, ")
                .append(expr.getCode())
                .append(")")
                .append(typeCode);

        // Only use tmp var if outside assign stmt
        String code;

        if (ASSIGN_STMT.check(node.getParent()))
            code = arrayCode.toString();
        else {
            code = ollirTypes.nextTemp() + typeCode;

            computation.append(code)
                    .append(SPACE)
                    .append(ASSIGN)
                    .append(typeCode)
                    .append(SPACE)
                    .append(arrayCode)
                    .append(END_STMT);
        }

        return new OllirExprResult(code, computation);
    }

    private OllirExprResult visitArrayInit(JmmNode node, Void unused) {
        StringBuilder computation = new StringBuilder();

        Type arrayType = types.getExprType(node.getChild(0));
        String rawTypeCode = ollirTypes.toOllirType(arrayType);
        String typeCode = ".array" + rawTypeCode;

        String tmp = ollirTypes.nextTemp();
        String code = tmp + typeCode;
        int arraySize = node.getNumChildren();

        // First create the Array object
        computation
                .append(code)
                .append(SPACE)
                .append(ASSIGN)
                .append(typeCode)
                .append(SPACE)
                .append("new(array, ")
                .append(arraySize)
                .append(OptUtils.i32_t)
                .append(")")
                .append(typeCode)
                .append(END_STMT);

        // Then set the values
        for (int i=0; i<arraySize; i++) {
            var child = node.getChild(i);
            var expr = visit(child);

            computation.append(expr.getComputation())
                    .append(tmp)
                    .append("[")
                    .append(i)
                    .append(OptUtils.i32_t)
                    .append("]")
                    .append(rawTypeCode)
                    .append(SPACE)
                    .append(ASSIGN)
                    .append(rawTypeCode)
                    .append(SPACE)
                    .append(expr.getCode())
                    .append(END_STMT);
        }

        return new OllirExprResult(code, computation);
    }

    private OllirExprResult visitArrayAccess(JmmNode node, Void unused) {
        JmmNode arrayNode = node.getChild(0);
        JmmNode parent = node.getParent();

        var expr1 = visit(arrayNode);
        var expr2 = visit(node.getChild(1));

        StringBuilder computation = new StringBuilder();
        computation.append(expr1.getComputation());
        computation.append(expr2.getComputation());

        String typeName = types.getExprType(node).getName();
        String typeCode = ollirTypes.toOllirType(typeName);

        // Special case for setting array value
        // i.e. node is LHS of AssignStmt

        if (Kind.ASSIGN_STMT.check(parent) && parent.getChild(0).equals(node)) {
            String arrayName = arrayNode.get("name");

            // Special case for String
            if (arrayName.equals("String"))
                arrayName = "\"String\"";

            StringBuilder code = new StringBuilder();
            if (types.isField(arrayNode))
                // Apparently code is different for fields (?)
                code.append(expr1.getCode());
            else
                code.append(arrayName);

            code.append("[")
                    .append(expr2.getCode())
                    .append("]")
                    .append(typeCode);

            return new OllirExprResult(code.toString(), computation);
        }

        // General case

        String code;
        StringBuilder arrayCode = new StringBuilder();

        arrayCode.append(expr1.getCode())
                .append("[")
                .append(expr2.getCode())
                .append("]")
                .append(typeCode);

        // Only use tmp var when parent != AssignStmt
        if (Kind.ASSIGN_STMT.check(parent))
            code = arrayCode.toString();
        else {
            code = ollirTypes.nextTemp() + typeCode;

            computation.append(code)
                    .append(SPACE)
                    .append(ASSIGN)
                    .append(typeCode)
                    .append(SPACE)
                    .append(arrayCode)
                    .append(END_STMT);
        }

        return new OllirExprResult(code, computation);
    }

    private OllirExprResult visitMethodCall(JmmNode node, Void unused) {
        JmmNode objectNode = node.getChild(0);
        var objectExpr = visit(objectNode);

        StringBuilder computation = new StringBuilder();
        computation.append(objectExpr.getComputation());

        // if class ref, i.e. static call
        boolean isStatic = types.getExprType(objectNode) == null;

        // 1. Create call code (with params)
        StringBuilder callCode = new StringBuilder();
        List<String> args = new ArrayList<>();

        callCode.append(isStatic ? "invokestatic" : "invokevirtual")
                .append("(")
                .append(objectExpr.getCode())
                .append(", \"")
                .append(node.get("name"))
                .append("\"");

        for (int i = 1; i < node.getNumChildren(); i++) {
            var paramExpr = visit(node.getChild(i));
            computation.append(paramExpr.getComputation());
            args.add(paramExpr.getCode());
        }
        if (!args.isEmpty()) {
            callCode.append(", ")
                    .append(String.join(", ", args));
        }

        // 2. Get type of method call
        String typeCode;
        JmmNode parent = node.getParent();
        boolean insideExprStmt = EXPR_STMT.check(parent);
        boolean insideAssignStmt = ASSIGN_STMT.check(parent);

        Type type = types.getExprType(node);

        if (!TypeUtils.isTypeUnknown(type))
            typeCode = ollirTypes.toOllirType(type);
        else {
            // Type is unknown, needs to be inferred

            // RHS of AssignExpr- Assume type of LHS
            if (insideAssignStmt && parent.getChild(1).equals(node)) {
                Type lhsType = types.getExprType(parent.getChild(0));
                typeCode = ollirTypes.toOllirType(lhsType);
            }
            else if (ARRAY_ACCESS.check(parent) && parent.getChild(1).equals(node)) {
                typeCode = OptUtils.i32_t;
            }
            else if (ARRAY_CREATION.check(parent) && parent.getChild(1).equals(node)) {
                typeCode = OptUtils.i32_t;
            }
            else if (UNARY_EXPR.check(parent) || LOGIC_EXPR.check(parent)) {
                typeCode = OptUtils.bool_t;
            }
            else if (BINARY_EXPR.check(parent)) {
                String op = parent.get("op");
                if (op.equals("<") || op.equals(">"))
                    typeCode = OptUtils.bool_t;
                else
                    typeCode = OptUtils.i32_t;
            }
            else if (insideExprStmt) {
                typeCode = OptUtils.void_t;
            }
            // In ReturnStmt- Assume function return type
            else if (RETURN_STMT.check(parent)) {
                JmmNode enclMethodName = TypeUtils.getEnclosingMethod(parent);
                Type retType = table.getReturnType(enclMethodName.get("name"));
                typeCode = ollirTypes.toOllirType(retType);
            }
            // It is actually not possible to infer type of array (in LengthAccess)
            // for our compiler since it supports arrays of any type, but we'll allow
            // it just in case there are any tests
            else if (LENGTH_ACCESS.check(parent)) {
                typeCode = ".array" + OptUtils.i32_t;
            }
            // !
            else if (ARRAY_INIT.check(parent)) {
                typeCode = OptUtils.i32_t;
            }
            // Not possible to reliably infer type, error
            else {
                throw new IllegalStateException("Unable to infer type of imported method");
            }
        }

        callCode.append(")")
                .append(typeCode);

        // 3. Check whether to use temp variable
        String code;

        if (insideAssignStmt) {
            // no computation, code is call code
            code = callCode.toString();
        }
        else if (insideExprStmt) {
            // no code, computation is call code
            code = "";
            computation
                    .append(callCode)
                    .append(END_STMT);
        }
        else {
            // use tmp var
            code = ollirTypes.nextTemp() + typeCode;

            computation.append(code)
                    .append(SPACE)
                    .append(ASSIGN)
                    .append(typeCode)
                    .append(SPACE)
                    .append(callCode)
                    .append(END_STMT);
        }

        return new OllirExprResult(code, computation);
    }

    private OllirExprResult visitVarRef(JmmNode node, Void unused) {

        String varName = node.get("name");
        JmmNode method = TypeUtils.getEnclosingMethod(node);

        String nVarName = varName;
        // Special case for variables named String
        if (nVarName.equals("String"))
            nVarName = "\"String\"";

        if (method != null) {
            // If param or local, return name
            String mname = method.get("name");

            for (Symbol v : table.getParameters(mname)) { // Param
                if (v.getName().equals(varName)) {
                    String ollirType = ollirTypes.toOllirType(v.getType());
                    return new OllirExprResult(nVarName + ollirType);
                }
            }
            for (Symbol v : table.getLocalVariables(mname)) { // Local
                if (v.getName().equals(varName)) {
                    String ollirType = ollirTypes.toOllirType(v.getType());
                    return new OllirExprResult(nVarName + ollirType);
                }
            }
        }

        // If field, use getfield instruction
        for (Symbol f : table.getFields()) {
            if (f.getName().equals(varName)) {
                String typeCode = ollirTypes.toOllirType(f.getType());
                String code = ollirTypes.nextTemp() + typeCode;

                StringBuilder computation = new StringBuilder()
                        .append(code)
                        .append(SPACE)
                        .append(ASSIGN)
                        .append(typeCode)
                        .append(SPACE)
                        .append("getfield(this, ")
                        .append(nVarName)
                        .append(typeCode)
                        .append(")")
                        .append(typeCode)
                        .append(END_STMT);

                return new OllirExprResult(code, computation);
            }
        }

        // Static reference to class, return class name
        return new OllirExprResult(nVarName);
    }

    /**
     * Default visitor. Visits every child node and return an empty result.
     */
    private OllirExprResult defaultVisit(JmmNode node, Void unused) {

        for (var child : node.getChildren()) {
            visit(child);
        }

        return OllirExprResult.EMPTY;
    }
}
