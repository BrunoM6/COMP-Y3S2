package pt.up.fe.comp2025.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;
import pt.up.fe.comp2025.ast.Kind;
import pt.up.fe.comp2025.ast.TypeUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class IncompatibleReturn extends AnalysisVisitor {

    private String currentMethod;
    private final TypeUtils typeUtils;

    private final Set<String> initialized = new HashSet<>();

    public IncompatibleReturn(SymbolTable symbolTable) {
        this.typeUtils = new TypeUtils(symbolTable, getReports());
    }

    @Override
    public void buildVisitor() {
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        addVisit(Kind.ASSIGN_STMT, this::visitAssignStmt);
        addVisit(Kind.RETURN_STMT, this::visitReturnStmt);
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        // clear previous state and add fields, params and imported vars in start
        initialized.clear();
        for (Symbol f : table.getFields()) {
            initialized.add(f.getName());
        }

        for (Symbol p : table.getParameters(method.get("name"))) {
            initialized.add(p.getName());
        }

        for (Symbol v : table.getLocalVariables(method.get("name"))) {
            if (typeUtils.isTypeImported(v.getType())) {
                initialized.add(v.getName());
            }
        }

        currentMethod = method.get("name");
        Type returnType = table.getReturnType(currentMethod);

        List<JmmNode> returnNodes = method.getChildren(Kind.RETURN_STMT);

        if (returnType.equals(TypeUtils.newVoidType())) {
            // Void method with return statement
            if (!returnNodes.isEmpty()) {
                String message = String.format(
                        "Void method '%s' must not return a value",
                        currentMethod
                );
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        method.getLine(),
                        method.getColumn(),
                        message,
                        null)
                );
                return null;
            }
        }

        else {
            if (returnNodes.isEmpty()) {
                // Non void method missing return stmt
                String message = String.format(
                        "Non-void method '%s' must return a value",
                        currentMethod
                );
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        method.getLine(),
                        method.getColumn(),
                        message,
                        null)
                );
                return null;
            }

            if (returnNodes.size() > 1) {
                // Multiple return statements
                String message = String.format(
                        "Method '%s' cannot have multiple return statements",
                        currentMethod
                );
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        method.getLine(),
                        method.getColumn(),
                        message,
                        null)
                );
                return null;
            }

            if (!Kind.RETURN_STMT.check(method.getChildren().getLast())) {
                // Last statement not a return statement
                String message = String.format(
                        "Last statement in method '%s' must be a return statement",
                        currentMethod
                );
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        method.getLine(),
                        method.getColumn(),
                        message,
                        null)
                );
                return null;
            }
        }

        return null;
    }

    private Void visitAssignStmt(JmmNode assignNode, SymbolTable table) {
        JmmNode lhs = assignNode.getChildren().getFirst();

        // mark lhs
        if (lhs.getKind().equals(Kind.VAR_REF_EXPR.toString())) {
            initialized.add(lhs.get("name"));
        }
        return null;
    }

    private Void visitReturnStmt(JmmNode returnStmt, SymbolTable table) {

        Type expectedRType = table.getReturnType(currentMethod);

        JmmNode returnExpr = returnStmt.getChildren(Kind.EXPR).getFirst();
        Type returnType = typeUtils.getExprType(returnExpr);

        //> Incompatible return type

        if (!typeUtils.isAssignableAs(returnType, expectedRType)) {
            String message = String.format(
                    "Incompatible return type in method '%s': expected '%s' but got '%s'",
                    currentMethod,
                    expectedRType.print(),
                    returnType.print()
            );
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    returnStmt.getLine(),
                    returnStmt.getColumn(),
                    message,
                    null)
            );
        }

        // Check for uninitialized vars
        JmmNode stmt = returnStmt.getChildren(Kind.EXPR).getFirst();
        checkUninitializedInExpr(stmt);

        return null;
    }

    // Recursively check for expr unitialized var
    private void checkUninitializedInExpr(JmmNode node) {
        if (node.getKind().equals(Kind.VAR_REF_EXPR.toString())) {
            String varName = node.get("name");
            if (!initialized.contains(varName)) {
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        node.getLine(),
                        node.getColumn(),
                        String.format("Variable '%s' has not been initialized", varName),
                        null
                ));
            }
        }
        // Recurse into all children
        for (JmmNode child : node.getChildren()) {
            checkUninitializedInExpr(child);
        }
    }
}
