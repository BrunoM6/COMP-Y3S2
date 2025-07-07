package pt.up.fe.comp2025.optimization;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.JmmNodeImpl;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;
import pt.up.fe.comp2025.ast.Kind;

import java.util.List;

public class ConstantFoldingVisitor extends AnalysisVisitor {
    private boolean modified = false;

    @Override
    public void buildVisitor() {
        addVisit(Kind.BINARY_EXPR, this::visitBinExpr);
        addVisit(Kind.LOGIC_EXPR, this::visitLogicExpr);
        addVisit(Kind.UNARY_EXPR, this::visitUnaryExpr);
    }

    public Boolean hasModified() {
        return modified;
    }

    public void resetModified() {
        this.modified = false;
    }

    private Void visitBinExpr(JmmNode binExprNode, SymbolTable symbolTable) {
        // Get both side nodes
        JmmNode lhs = binExprNode.getChildren().get(0);
        JmmNode rhs = binExprNode.getChildren().get(1);

        // Index associated with this expression to replace and parent so we can replace
        int nodeIndex = binExprNode.getParent().getChildren().indexOf(binExprNode);
        JmmNode parentNode = binExprNode.getParent();
        if (lhs.getKind().equals(Kind.INTEGER_LIT.toString()) && rhs.getKind().equals(Kind.INTEGER_LIT.toString())) {
            Integer lhsValue = Integer.parseInt(lhs.get("value"));
            Integer rhsValue = Integer.parseInt(rhs.get("value"));

            // Initialize result variables
            Integer result = null;
            Boolean booleanResult = null;

            switch (binExprNode.get("op")) {
                case "+":
                    result = lhsValue + rhsValue;
                    break;
                case "-":
                    result = lhsValue - rhsValue;
                    break;
                case "*":
                    result = lhsValue * rhsValue;
                    break;
                case "/":
                    result = lhsValue / rhsValue;
                    break;
                /*case "<":
                    booleanResult = lhsValue < rhsValue;
                    break;
                case ">":
                    booleanResult = lhsValue > rhsValue;
                    break;*/
                // Must be one of the defined operators
                default:
                    return null;
            }

            // Create literal node and replace the expression with it if it exists
            JmmNode literal;
            if (booleanResult != null) {
                literal = new JmmNodeImpl(List.of(Kind.BOOLEAN_LIT.toString()));
                literal.put("value", booleanResult.toString());
            } else if (result != null) {
                literal = new JmmNodeImpl(List.of(Kind.INTEGER_LIT.toString()));
                literal.put("value", result.toString());
            }
            else {
                return null;
            }

            parentNode.setChild(literal, nodeIndex);
            modified = true;
        }

        return null;
    }

    private Void visitLogicExpr(JmmNode logicExprNode, SymbolTable symbolTable) {
        // Get both side nodes
        JmmNode lhs = logicExprNode.getChildren().get(0);
        JmmNode rhs = logicExprNode.getChildren().get(1);

        // Index associated with this expression to replace and parent so we can replace
        int nodeIndex = logicExprNode.getParent().getChildren().indexOf(logicExprNode);
        JmmNode parentNode = logicExprNode.getParent();

        if (lhs.getKind().equals(Kind.BOOLEAN_LIT.toString()) && rhs.getKind().equals(Kind.BOOLEAN_LIT.toString())) {
            Boolean lhsValue = Boolean.parseBoolean(lhs.get("value"));
            Boolean rhsValue = Boolean.parseBoolean(rhs.get("value"));

            Boolean result = null;
            switch (logicExprNode.get("op")) {
                case "&&":
                    result = lhsValue && rhsValue;
                    break;
                case "||":
                    result = lhsValue || rhsValue;
                    break;
                default:
                    return null;
            }
            JmmNode literal = new JmmNodeImpl(List.of(Kind.BOOLEAN_LIT.toString()));
            literal.put("value", result.toString());
            parentNode.setChild(literal, nodeIndex);
            modified = true;
        }
        return null;
    }

    private Void visitUnaryExpr(JmmNode unaryExprNode, SymbolTable symbolTable) {
        // Get the operand
        JmmNode operand = unaryExprNode.getChildren().get(0);

        // If we have a simplified operand already, fold it
        if (operand.getKind().equals(Kind.BOOLEAN_LIT.toString())) {
            int nodeIndex = unaryExprNode.getParent().getChildren().indexOf(unaryExprNode);
            JmmNode parentNode = unaryExprNode.getParent();
            String op = unaryExprNode.get("op");
            if (!op.equals("!")) {
                throw new RuntimeException("Unexpected unary operator: " + op);
            }
            Boolean booleanValue = Boolean.parseBoolean(operand.get("value"));
            booleanValue = !booleanValue;
            JmmNode literal = new JmmNodeImpl(List.of(Kind.BOOLEAN_LIT.toString()));
            literal.put("value", booleanValue.toString());
            parentNode.setChild(literal, nodeIndex);
            modified = true;
        }
        return null;
    }
}
