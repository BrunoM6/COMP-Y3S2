package pt.up.fe.comp2025.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;
import pt.up.fe.comp2025.ast.Kind;
import pt.up.fe.comp2025.optimization.OllirExprResult;

public class InvalidExprStmt extends AnalysisVisitor {

    @Override
    public void buildVisitor() {
        addVisit(Kind.EXPR_STMT, this::visitExprStmt);
    }

    private Void visitExprStmt(JmmNode exprStmt, SymbolTable symbolTable) {

        // Expression statements (expr ';') can only be method calls,
        // otherwise they have no meaning (example a;)

        JmmNode child = exprStmt.getChildren(Kind.EXPR).getFirst();

        if (!Kind.METHOD_CALL.check(child) && !Kind.OBJECT_CREATION.check(child)) {
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    exprStmt.getLine(),
                    exprStmt.getColumn(),
                    "Not a statement",
                    null)
            );
        }

        return null;
    }
}
