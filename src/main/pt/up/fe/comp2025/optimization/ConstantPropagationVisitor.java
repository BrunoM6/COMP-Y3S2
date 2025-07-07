package pt.up.fe.comp2025.optimization;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.JmmNodeImpl;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;
import pt.up.fe.comp2025.ast.Kind;

import java.util.*;

public class ConstantPropagationVisitor extends AnalysisVisitor {
    private Map<String, Object> constantValues = new HashMap<>();
    private boolean modified = false;

    @Override
    public void buildVisitor() {
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        addVisit(Kind.IF_STMT, this::visitIfStmt);
        addVisit(Kind.WHILE_STMT, this::visitWhileStmt);
        addVisit(Kind.ASSIGN_STMT, this::visitAssignStmt);
        addVisit(Kind.VAR_REF_EXPR, this::visitVarRefExpr);
    }

    public Boolean hasModified() {
        return modified;
    }

    public void resetModified() {
        this.modified = false;
    }

    private Void visitMethodDecl(JmmNode methodNode, SymbolTable symbolTable) {
        // Reset constant values for each method (scope boundary)
        constantValues.clear();

        return null;
    }

    private Void visitIfStmt(JmmNode ifNode, SymbolTable table) {
        // Propagate into "if" condition
        JmmNode cond = ifNode.getChildren().getFirst();
        visit(cond, table);

        // Save constants
        Map<String,Object> savedState = new HashMap<>(constantValues);

        // Visit “then” branch with a fresh copy
        visit(ifNode.getChildren().get(1), table);
        Map<String,Object> thenMap = new HashMap<>(constantValues);;

        // Visit “else” branch
        if (ifNode.getNumChildren() == 3) {
            visit(ifNode.getChildren().get(2), table);
        }
        Map<String,Object> elseMap = new HashMap<>(constantValues);;

        // Merge them
        Map<String, Object> merged = new HashMap<>();
        for (String var : thenMap.keySet()) {
            if(elseMap.containsKey(var) && thenMap.get(var).equals(elseMap.get(var))) {
                merged.put(var, thenMap.get(var));
            }
        }

        // restore the merged state as “current”
        constantValues = merged;

        return null;
    }

    private Void visitWhileStmt(JmmNode whileNode, SymbolTable table) {
        JmmNode condition = whileNode.getChildren().get(0);
        JmmNode body = whileNode.getChildren().get(1);

        // Collect all variables assigned anywhere inside body of while loop
        Set<String> assigned = new HashSet<>();
        collectAssignedVars(body, assigned);

        // Build condMap = savedMap - any loop-assigned var
        Map<String, Object> savedMap = new HashMap<>(constantValues);
        Map<String, Object> condMap = new HashMap<>(savedMap);
        for (String var : assigned) {
            condMap.remove(var);
        }

        // Temporarily swap in condMap so that “visit(condition)” does not taint savedMap
        Map<String, Object> backup = constantValues;
        constantValues = new HashMap<>(condMap);
        visit(condition, table);
        // Discard any changes that visiting the condition might have made.
        constantValues = new HashMap<>(condMap);

        // Now visit the body, starting from condMap
        visit(body, table);

        // After visiting the body, constantValues may have removed some vars that occurred
        for (String var : assigned) {
            constantValues.remove(var);
        }

        // 4) Restore any loop-invariant constants that were in savedMap but never actually killed
        for (Map.Entry<String,Object> e : savedMap.entrySet()) {
            String var = e.getKey();
            Object val = e.getValue();
            if (!assigned.contains(var) && !constantValues.containsKey(var)) {
                constantValues.put(var, val);
            }
        }

        return null;
    }


    private Void visitAssignStmt(JmmNode assignNode, SymbolTable symbolTable) {
        List<JmmNode> assignChildren = assignNode.getChildren();

        // Get the left-hand and right-hand side of assign statements
        JmmNode lhs = assignChildren.get(0);

        // Left-hand side must be a variable being assigned
        if (!lhs.getKind().equals(Kind.VAR_REF_EXPR.toString())) {
            return null;
        }
        String varName = lhs.get("name");

        // Right-hand side must be boolean, integer literal or previously obtained constant variable
        // Visit right hand side to propagate constants
        JmmNode rhs = assignChildren.get(1);
        visit(rhs, symbolTable);
        if (rhs.getKind().equals(Kind.INTEGER_LIT.toString())) {
            int value = Integer.parseInt(rhs.get("value"));
            constantValues.put(varName, value);
        }
        else if (rhs.getKind().equals(Kind.BOOLEAN_LIT.toString())) {
            boolean value = Boolean.parseBoolean(rhs.get("value"));
            constantValues.put(varName, value ? 1 : 0);
        }
        else if (rhs.getKind().equals(Kind.VAR_REF_EXPR.toString())) {
            String rhsVar = rhs.get("name");

            // Propagation from one variable to another
            if (constantValues.containsKey(rhsVar)) {
                constantValues.put(varName, constantValues.get(rhsVar));
            } else {
                constantValues.remove(varName);
            }
        }
        // Can no longer trust the lhs value to be constant
        else {
            constantValues.remove(varName);
        }
        // If not simple value, nothing to be done
        // Keep in mind that for complex expressions, this gets called multiple times
        // (while(modified)) -> successively optimize expressions and values using simplest operation
        return null;
    }

    private Void visitVarRefExpr(JmmNode varNode, SymbolTable symbolTable) {
        // Check if the parent is an assign statement on the variable, skip if so
        JmmNode parent = varNode.getParent();
        if (parent.getKind().equals(Kind.ASSIGN_STMT.toString())
                && parent.getChildren().indexOf(varNode) == 0) {
            return null;
        }

        // Variable name
        String varName = varNode.get("name");

        // If we have seen this variable before
        if (constantValues.containsKey(varName)) {
            Object value = constantValues.get(varName);

            JmmNode parentNode = varNode.getParent();
            int nodeIndex = parentNode.getChildren().indexOf(varNode);

            // Create the actual literal that the variable is referencing
            JmmNode literal;
            if (value instanceof Integer) {
                List<String> attributes = List.of(Kind.INTEGER_LIT.toString());
                literal = new JmmNodeImpl(attributes);
                literal.put("value", value.toString());
                modified = true;
            }
            else if (value instanceof Boolean) {
                List<String> attributes = List.of(Kind.BOOLEAN_LIT.toString());
                literal = new JmmNodeImpl(attributes);
                literal.put("value", value.toString());
                modified = true;
            }
            else {
                return null;
            }

            parentNode.setChild(literal, nodeIndex);
            modified = true;
        }
        return null;
    }

    // Helper to traverse a 'node' and collect all assigned vars in it
    private void collectAssignedVars(JmmNode node, Set<String> assigned) {
        if (node.getKind().equals(Kind.ASSIGN_STMT.toString())) {
            JmmNode lhs = node.getChildren().get(0);
            if (lhs.getKind().equals(Kind.VAR_REF_EXPR.toString())) {
                assigned.add(lhs.get("name"));
            }
        }
        for (JmmNode child : node.getChildren()) {
            collectAssignedVars(child, assigned);
        }
    }
}
