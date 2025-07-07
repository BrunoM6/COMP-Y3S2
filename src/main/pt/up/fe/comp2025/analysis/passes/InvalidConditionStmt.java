package pt.up.fe.comp2025.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;
import pt.up.fe.comp2025.ast.Kind;
import pt.up.fe.comp2025.ast.TypeUtils;

public class InvalidConditionStmt extends AnalysisVisitor {

    private final TypeUtils typeUtils;

    public InvalidConditionStmt(SymbolTable symbolTable) {
        this.typeUtils = new TypeUtils(symbolTable, getReports());
    }

    @Override
    public void buildVisitor() {
        addVisit(Kind.IF_STMT, this::handleConditionStmt);
        addVisit(Kind.WHILE_STMT, this::handleConditionStmt);
    }

    private Void handleConditionStmt(JmmNode stmt, SymbolTable table) {

        // IF(EXPR) STMT
        JmmNode exprNode = stmt.getChildren(Kind.EXPR).getFirst();
        Type exprType = typeUtils.getExprType(exprNode);

        //> Expressions in conditions must return a boolean (if(2+3) is an error)
        if (!typeUtils.isAssignableAs(exprType, TypeUtils.newBooleanType())) {

            Kind k = Kind.fromString(stmt.getKind());
            String kStr = k == Kind.IF_STMT ? "if" : "while";
            String message = String.format("Expression in '%s' condition must be of type boolean", kStr);

            addReport(Report.newError(
                    Stage.SEMANTIC,
                    exprNode.getLine(),
                    exprNode.getColumn(),
                    message,
                    null)
            );
        }

        return null;
    }
}
