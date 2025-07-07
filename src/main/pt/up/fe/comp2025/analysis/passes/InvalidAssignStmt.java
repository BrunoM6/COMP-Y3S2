package pt.up.fe.comp2025.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;
import pt.up.fe.comp2025.ast.Kind;
import pt.up.fe.comp2025.ast.TypeUtils;

public class InvalidAssignStmt extends AnalysisVisitor {

    private final TypeUtils typeUtils;

    public InvalidAssignStmt(SymbolTable symbolTable) {
        this.typeUtils = new TypeUtils(symbolTable, getReports());
    }

    @Override
    public void buildVisitor() {
        addVisit(Kind.ASSIGN_STMT, this::handleAssignStmt);
    }

    private Void handleAssignStmt(JmmNode stmt, SymbolTable table) {

        // EXPR = EXPR;
        JmmNode assigneeNode = stmt.getChildren(Kind.EXPR).get(0);
        JmmNode assignedNode = stmt.getChildren(Kind.EXPR).get(1);

        Type assigneeType = typeUtils.getExprType(assigneeNode);
        Type assignedType = typeUtils.getExprType(assignedNode);

        //> Type of the assignee must be compatible with the assigned

        // "this" cannot be reassigned
        if (assigneeNode.getKind().equals(Kind.THIS_REF.toString())) {
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    assigneeNode.getLine(),
                    assigneeNode.getColumn(),
                    "'this' is the class instance and cannot be reassigned",
                    null)
            );
        }

        if (!typeUtils.isAssignableAs(assignedType, assigneeType)) {
            String message = String.format("Types '%s' and '%s' in assign statement are not compatible",
                    assigneeType.print(),
                    assignedType.print());

            addReport(Report.newError(
                    Stage.SEMANTIC,
                    assigneeNode.getLine(),
                    assigneeNode.getColumn(),
                    message,
                    null)
            );
        }

        return null;
    }
}
