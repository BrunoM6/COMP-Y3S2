package pt.up.fe.comp2025.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;
import pt.up.fe.comp2025.ast.Kind;
import pt.up.fe.comp2025.ast.TypeUtils;

import java.util.List;

public class InvalidArrayExpr extends AnalysisVisitor {

    private final TypeUtils typeUtils;

    public InvalidArrayExpr(SymbolTable symbolTable) {
        this.typeUtils = new TypeUtils(symbolTable, getReports());
    }

    @Override
    public void buildVisitor() {
        addVisit(Kind.ARRAY_ACCESS, this::handleArrayAccess);
        addVisit(Kind.ARRAY_INIT, this::handleArrayInit);
        addVisit(Kind.ARRAY_CREATION, this::handleArrayCreation);
    }

    private Void handleArrayAccess(JmmNode arrayExpr, SymbolTable table) {

        // EXPR[EXPR]
        JmmNode leftNode = arrayExpr.getChildren(Kind.EXPR).get(0);
        JmmNode rightNode = arrayExpr.getChildren(Kind.EXPR).get(1);

        Type leftType = typeUtils.getExprType(leftNode);
        Type rightType = typeUtils.getExprType(rightNode);

        //> Array access is done over an array
        if (!leftType.isArray()) {
            String message = "Array access must be done over an array expression";
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    leftNode.getLine(),
                    leftNode.getColumn(),
                    message,
                    null)
            );
        }

        //> Array access index is an expression of type integer
        if (!typeUtils.isAssignableAs(rightType, TypeUtils.newIntType())) {
            String message = String.format(
                    "Array access index must be an expression of type 'int', not '%s'",
                    rightType.print()
            );
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    rightNode.getLine(),
                    rightNode.getColumn(),
                    message,
                    null)
            );
        }

        return null;
    }

    private Void handleArrayInit(JmmNode arrayExpr, SymbolTable table) {

        // Check integrity of array initializer, i.e.,
        // make sure all elements are of the same type

        List<JmmNode> elements = arrayExpr.getChildren(Kind.EXPR);

        if (elements.isEmpty()) {
            // Empty array, no need to check
            return null;
        }

        Type type = typeUtils.getExprType(elements.getFirst());
        for (JmmNode e : elements) {
            // skip first?
            Type t = typeUtils.getExprType(e);

            // Array of array is not supported
            if (t.isArray()) {
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        arrayExpr.getLine(),
                        arrayExpr.getColumn(),
                        "Array of array initialization is not allowed",
                        null)
                );
            }

            // todo: check if A is assignable as B and vice versa?
            if (!t.equals(type)) {
                String message = String.format(
                        "Types '%s' and '%s' in array initializer do not match",
                        type.print(),
                        t.print()
                );
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        arrayExpr.getLine(),
                        arrayExpr.getColumn(),
                        message,
                        null)
                );
            }
        }

        return null;
    }

    private Void handleArrayCreation(JmmNode arrayExpr, SymbolTable table) {

        // Block creation of an "array of array"??
        // example: new int[][6]
        JmmNode typeNode = arrayExpr.getChildren(Kind.TYPE).getFirst();
        Type type = TypeUtils.convertType(typeNode);

        if (type.isArray()) {
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    arrayExpr.getLine(),
                    arrayExpr.getColumn(),
                    "Array creation cannot be done over an array expression",
                    null)
            );
        }

        // Size in array creation must be of type int
        // example: new int[true] is not allowed;
        JmmNode exprNode = arrayExpr.getChildren(Kind.EXPR).getFirst();
        Type exprType = typeUtils.getExprType(exprNode);

        if (!typeUtils.isAssignableAs(exprType, TypeUtils.newIntType())) {
            String message = String.format(
                    "Array size be an expression of type 'int', not '%s'",
                    exprType.print()
            );
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
