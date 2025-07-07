package pt.up.fe.comp2025.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;
import pt.up.fe.comp2025.ast.Kind;
import pt.up.fe.comp2025.ast.TypeUtils;

// Also handles LogicExpr, which is technically a binary expr
public class InvalidBinaryExpr extends AnalysisVisitor {

    private final TypeUtils typeUtils;

    public InvalidBinaryExpr(SymbolTable symbolTable) {
        this.typeUtils = new TypeUtils(symbolTable, getReports());
    }

    @Override
    public void buildVisitor() {
        addVisit(Kind.BINARY_EXPR, this::handleBinaryExpr);
        addVisit(Kind.LOGIC_EXPR, this::handleBinaryExpr);
    }

    private Void handleBinaryExpr(JmmNode binaryExpr, SymbolTable table) {

        String op = binaryExpr.get("op");
        JmmNode nodeLeft = binaryExpr.getChildren(Kind.EXPR).get(0);
        JmmNode nodeRight = binaryExpr.getChildren(Kind.EXPR).get(1);

        Type typeLeft = typeUtils.getExprType(nodeLeft);
        Type typeRight = typeUtils.getExprType(nodeRight);

        //> Array cannot be used in arithmetic operations
        JmmNode target = typeLeft.isArray() ? nodeLeft
                : (typeRight.isArray() ? nodeRight : null);

        if (target != null) {
            //String varName = target.get("name"); ??
            String message = String.format("An array cannot be used in binary operation '%s'", op);
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    target.getLine(),
                    target.getColumn(),
                    message,
                    null)
            );
        }

        boolean isLogic = op.equals("||") || op.equals("&&");
        Type expectedType = isLogic ? TypeUtils.newBooleanType() : TypeUtils.newIntType();

        // Check type
        // Int for arithmetic expr and boolean for logic expr
        if (typeUtils.isAssignableAs(typeLeft, expectedType) &&
        typeUtils.isAssignableAs(typeRight, expectedType)) {
            return null;
        }

        String message = String.format(
                "Operands of binary operation '%s' must be of type '%s'",
                op,
                expectedType.getName()
        );
        addReport(Report.newError(
                Stage.SEMANTIC,
                nodeLeft.getLine(),
                nodeLeft.getColumn(),
                message,
                null)
        );

        return null;
    }
}
