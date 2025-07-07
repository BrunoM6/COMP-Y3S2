package pt.up.fe.comp2025.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;
import pt.up.fe.comp2025.ast.Kind;
import pt.up.fe.specs.util.SpecsCheck;

public class InvalidThisExpr extends AnalysisVisitor {

    private String currentMethod;
    private boolean isMethodStatic;

    @Override
    public void buildVisitor() {
        addVisit(Kind.METHOD_DECL, this::handleMethodDecl);
        addVisit(Kind.THIS_REF, this::handleThisExpr);
        addVisit(Kind.ASSIGN_STMT, this::handleAssignStmt);
    }

    private Void handleMethodDecl(JmmNode method, SymbolTable table) {
        currentMethod = method.get("name");
        isMethodStatic = (boolean)method.getObject("isStatic");
        return null;
    }

    private Void handleThisExpr(JmmNode thisExpr, SymbolTable table) {

        SpecsCheck.checkNotNull(currentMethod, () -> "Expected current method to be set");

        //> “this” expression cannot be used in a static method
        if (isMethodStatic) {
            String message = String.format("'this' keyword cannot be used in a static method ('%s')",
                    currentMethod);
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    thisExpr.getLine(),
                    thisExpr.getColumn(),
                    message,
                    null)
            );
            return null;
        }
        return null;
    }

    private Void handleAssignStmt(JmmNode assignStmt, SymbolTable table) {

        //> “this” can be used as an “object” (e.g. A a; a = this;)
        //...
        return null;
    }
}
