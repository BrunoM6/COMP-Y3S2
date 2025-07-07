package pt.up.fe.comp2025.optimization;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2025.ast.TypeUtils;

import java.util.List;
import java.util.stream.Collectors;

import static pt.up.fe.comp2025.ast.Kind.*;

/**
 * Generates OLLIR code from JmmNodes that are not expressions.
 */
public class OllirGeneratorVisitor extends AJmmVisitor<Void, String> {

    private static final String SPACE = " ";
    private static final String ASSIGN = ":=";
    private final String END_STMT = ";\n";
    private final String NL = "\n";
    private final String L_BRACKET = " {\n";
    private final String R_BRACKET = "}\n";

    private final SymbolTable table;

    private final TypeUtils types;
    private final OptUtils ollirTypes;

    private final OllirExprGeneratorVisitor exprVisitor;

    public OllirGeneratorVisitor(SymbolTable table) {
        this.table = table;
        this.types = new TypeUtils(table);
        this.ollirTypes = new OptUtils();
        exprVisitor = new OllirExprGeneratorVisitor(table, ollirTypes);
    }

    @Override
    protected void buildVisitor() {
        addVisit(PROGRAM, this::visitProgram);
        addVisit(IMPORT_DECL, this::visitImportDecl);
        addVisit(CLASS_DECL, this::visitClass);
        //addVisit(VAR_DECL, this::visitVarDecl);
        addVisit(METHOD_DECL, this::visitMethodDecl);
        addVisit(PARAM, this::visitParam);
        addVisit(BRACKET_STMT, this::visitBracketStmt);
        addVisit(IF_STMT, this::visitIfStmt);
        addVisit(WHILE_STMT, this::visitWhileStmt);
        addVisit(EXPR_STMT, this::visitExprStmt);
        addVisit(ASSIGN_STMT, this::visitAssignStmt);
        addVisit(RETURN_STMT, this::visitReturn);
//        setDefaultVisit(this::defaultVisit);
    }

    private String visitProgram(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();

        node.getChildren().stream()
                .map(this::visit)
                .forEach(code::append);

        return code.toString();
    }

    private String buildConstructor() {
        // No need to expand since the grammar
        // doesn't support constructors
        return """
                .construct %s().V {
                    invokespecial(this, "<init>").V;
                }
                """.formatted(table.getClassName());
    }

    // Only used for fields, not local variables
    private String visitVarDecl(JmmNode node) {
        StringBuilder code = new StringBuilder();

        code.append(".field public ");
        code.append(node.get("name"));

        JmmNode typeNode = node.getChild(0);
        Type type = TypeUtils.convertType(typeNode);
        String typeCode = ollirTypes.toOllirType(type);

        code.append(typeCode);
        code.append(END_STMT);
        return code.toString();
    }

    private String visitImportDecl(JmmNode node, Void unused) {
        List<Object> importList = node.getObjectAsList("pkgName");
        String importString = importList.stream()
                .map(Object::toString)
                .collect(Collectors.joining("."));

        return "import " + importString + ";" + NL;
    }

    private String visitClass(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();
        code.append(NL);
        code.append(table.getClassName());

        // Superclass code
        String superClass = table.getSuper();
        if (superClass != null && !superClass.isEmpty()) {
            code.append(" extends ");
            code.append(superClass);
        }

        code.append(L_BRACKET);
        //code.append(NL);

        // Code for fields
        for (var child : node.getChildren(VAR_DECL)) {
            var result = visitVarDecl(child);
            code.append(result);
        }

        code.append(NL);
        code.append(buildConstructor());
        code.append(NL);

        for (var child : node.getChildren(METHOD_DECL)) {
            var result = visit(child);
            code.append(result);
        }

        code.append(R_BRACKET);
        return code.toString();
    }

    private String visitMethodDecl(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder(".method ");

        boolean isPublic = node.getBoolean("isPublic", false);
        boolean isStatic = node.getBoolean("isStatic", false);

        if (isPublic)
            code.append("public ");
        if (isStatic)
            code.append("static ");

        var name = node.get("name");
        code.append(name);

        // Code for parameters
        var paramsCode = node.getChildren(PARAM).stream()
                .map(this::visit)
                .collect(Collectors.joining(", "));
        code.append("(");
        code.append(paramsCode);
        code.append(")");

        Type retType = table.getReturnType(name);
        var typeCode = ollirTypes.toOllirType(retType);
        code.append(typeCode);
        code.append(L_BRACKET);

        // Code for statements
        var stmtsCode = node.getChildren(STMT).stream()
                .map(this::visit)
                .collect(Collectors.joining("\n   ", "   ", ""));

        code.append(stmtsCode);

        // Return statement for void functions
        if (retType.equals(TypeUtils.newVoidType()))
            code.append("ret.V;").append(NL);

        code.append(R_BRACKET);
        code.append(NL);

        return code.toString();
    }

    private String visitParam(JmmNode node, Void unused) {
        JmmNode typeNode = node.getChild(0);
        Type type = TypeUtils.convertType(typeNode);
        String typeCode = ollirTypes.toOllirType(type);

        String id = node.get("name");
        return id + typeCode;
    }

    private String visitExprStmt(JmmNode node, Void unused) {
        var exprResult = exprVisitor.visit(node.getChild(0));
        return exprResult.getComputation();
    }

    private String visitBracketStmt(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();

        var innerStmts = node.getChildren(STMT).stream()
                .map(this::visit)
                .collect(Collectors.joining("\n   ", "   ", ""));

        code.append(innerStmts);
        return code.toString();
    }

    private String visitReturn(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();

        JmmNode method = TypeUtils.getEnclosingMethod(node);
        Type retType = table.getReturnType(method.get("name"));
        String typeCode = ollirTypes.toOllirType(retType);

        var expr = node.getNumChildren() > 0
                ? exprVisitor.visit(node.getChild(0))
                : OllirExprResult.EMPTY;

        code.append(expr.getComputation());

        code.append("ret");
        code.append(typeCode);
        code.append(SPACE);

        code.append(expr.getCode());
        code.append(END_STMT);

        return code.toString();
    }

    private String visitAssignStmt(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();

        JmmNode leftNode = node.getChild(0);
        JmmNode rightNode = node.getChild(1);

        Type lType = types.getExprType(leftNode);
        String typeCode = ollirTypes.toOllirType(lType);

        // Append RHS computation first? apparently not??

        // Special case for assignment of fields
        // use putfield instruction
        if (types.isField(leftNode)) {
            String fieldName = leftNode.get("name");

            // Special handling for String
            if (fieldName.equals("String"))
                fieldName = "\"String\"";

            var rhs = exprVisitor.visit(rightNode);

            code.append(rhs.getComputation())
                    .append("putfield(this, ")
                    .append(fieldName)
                    .append(typeCode)
                    .append(", ")
                    .append(rhs.getCode())
                    .append(").V")
                    .append(END_STMT);
        }
        // General case
        else {
            var lhs = exprVisitor.visit(leftNode);
            var rhs = exprVisitor.visit(rightNode);
            String varCode = lhs.getCode();

            code.append(lhs.getComputation())
                    .append(rhs.getComputation())
                    .append(varCode)
                    .append(SPACE)
                    .append(ASSIGN)
                    .append(typeCode)
                    .append(SPACE)
                    .append(rhs.getCode())
                    .append(END_STMT);
        }

        return code.toString();
    }

    private String visitIfStmt(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();

        String thenLabel = ollirTypes.nextTemp("then");
        String endLabel = ollirTypes.nextTemp("endif");

        var conditionResult = exprVisitor.visit(node.getChild(0));

        // X.computation
        code.append(conditionResult.getComputation());
        code.append(NL);

        // IF X.code GOTO then
        code.append("if (");
        code.append(conditionResult.getCode());
        code.append(") goto ");
        code.append(thenLabel);
        code.append(END_STMT);

        // "Else" code (if applicable)
        if (node.getNumChildren() > 2) {
            code.append(visit(node.getChild(2)));
        }

        code.append("goto ");
        code.append(endLabel);
        code.append(END_STMT);

        // "Then" label
        code.append(NL);
        code.append(thenLabel);
        code.append(":\n");

        // "Then/if" code
        code.append(visit(node.getChild(1)));

        // "Endif" label
        code.append(NL);
        code.append(endLabel);
        code.append(":\n");

        return code.toString();
    }

    private String visitWhileStmt(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();

        String whileLabel = ollirTypes.nextTemp("while");
        String endLabel = ollirTypes.nextTemp("endwhile");

        // "While" label
        code.append(whileLabel);
        code.append(":\n");

        // Condition computation
        var conditionResult = exprVisitor.visit(node.getChild(0));
        code.append(conditionResult.getComputation());

        // Condition check
        code.append("if (!.bool ");
        code.append(conditionResult.getCode());
        code.append(") goto ");
        code.append(endLabel);
        code.append(END_STMT);

        // Condition code
        code.append(visit(node.getChild(1)));

        // Loop
        code.append("goto ");
        code.append(whileLabel);
        code.append(END_STMT);

        // "Endwhile" label
        code.append(NL);
        code.append(endLabel);
        code.append(":\n");

        return code.toString();
    }

    /**
     * Default visitor. Visits every child node and return an empty string.
     */
    private String defaultVisit(JmmNode node, Void unused) {

        for (var child : node.getChildren()) {
            visit(child);
        }

        return "";
    }
}
