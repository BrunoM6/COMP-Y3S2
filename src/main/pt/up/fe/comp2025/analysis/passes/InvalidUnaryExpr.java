package pt.up.fe.comp2025.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;
import pt.up.fe.comp2025.ast.Kind;
import pt.up.fe.comp2025.ast.TypeUtils;

public class InvalidUnaryExpr extends AnalysisVisitor {

    private final TypeUtils typeUtils;

    public InvalidUnaryExpr(SymbolTable symbolTable) {
        this.typeUtils = new TypeUtils(symbolTable, getReports());
    }

    @Override
    public void buildVisitor() {
        addVisit(Kind.UNARY_EXPR, this::handleUnaryExpr);
    }

    public Void handleUnaryExpr(JmmNode unaryExpr, SymbolTable table) {

        String op = unaryExpr.get("op");
        JmmNode exprNode = unaryExpr.getChildren(Kind.EXPR).getFirst();
        Type exprType = typeUtils.getExprType(exprNode);

        // Check if boolean
        if (typeUtils.isAssignableAs(exprType, TypeUtils.newBooleanType())) {
            return null;
        }

        String message = String.format(
                "Operand of unary operation '%s' must be of type 'boolean'",
                op
        );
        addReport(Report.newError(
                Stage.SEMANTIC,
                exprNode.getLine(),
                exprNode.getColumn(),
                message,
                null)
        );

        return null;
    }
}
