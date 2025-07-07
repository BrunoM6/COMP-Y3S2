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

import java.util.HashSet;
import java.util.Set;

public class UninitializedVariable extends AnalysisVisitor {

    private String currentMethod;
    private final TypeUtils typeUtils;

    // Tracks initialization status of variables
    private final Set<String> initialized = new HashSet<>();

    public UninitializedVariable(SymbolTable symbolTable) {
        this.typeUtils = new TypeUtils(symbolTable, getReports());
    }

    @Override
    public void buildVisitor() {
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        addVisit(Kind.ASSIGN_STMT, this::visitAssignStmt);
        addVisit(Kind.VAR_REF_EXPR, this::visitVarRefExpr);
    }

    private Void visitMethodDecl(JmmNode methodNode, SymbolTable table) {
        // Clear previous method’s state and add fields, parameters and imported variables to be ignored
        initialized.clear();
        currentMethod = methodNode.get("name");

        for (Symbol f : table.getFields()) {
            initialized.add(f.getName());
        }

        for (Symbol p : table.getParameters(currentMethod)) {
            initialized.add(p.getName());
        }

        for (Symbol v : table.getLocalVariables(currentMethod)) {
            if (typeUtils.isTypeImported(v.getType())) {
                initialized.add(v.getName());
            }
        }

        // No need to traverse deeper here; assignments and var-refs are handled by their own visitors
        return null;
    }

    private Void visitAssignStmt(JmmNode assignNode, SymbolTable table) {
        JmmNode lhs = assignNode.getChildren().get(0);
        JmmNode rhs = assignNode.getChildren().get(1);

        // First, recursively check the RHS expression for any VAR_REF_EXPR used before assignment
        visit(rhs, table);

        // Now mark the LHS variable as initialized
        if (lhs.getKind().equals(Kind.VAR_REF_EXPR.toString())) {
            String varName = lhs.get("name");
            initialized.add(varName);
        }

        return null;
    }

    private Void visitVarRefExpr(JmmNode varNode, SymbolTable table) {
        // First check if this variable is declared at all:
        Type declaredType = typeUtils.getExprType(varNode);
        if (declaredType == null) {
            // If it’s undeclared, let the “undeclared variable” pass report it instead
            return null;
        }

        // If it’s a field, parameter, or local, we check “initialized”:
        String varName = varNode.get("name");

        // If varName is a class name or an imported class, skip (static‐class references)
        boolean classRef = varName.equals(table.getClassName());
        if (!classRef) {
            for (String imp : table.getImports()) {
                if (imp.equals(varName)) {
                    classRef = true;
                    break;
                }
            }
        }
        if (classRef) {
            // If used as the target of a static call, it’s okay, otherwise fall through.
            JmmNode parent = varNode.getParent();
            if (Kind.METHOD_CALL.check(parent) && parent.getChild(0).equals(varNode)) {
                return null;
            }
        }

        // Now, if the variable is not in “initialized”, that means it’s being used before assignment
        if (!initialized.contains(varName)) {
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    varNode.getLine(),
                    varNode.getColumn(),
                    String.format("Variable '%s' has not been initialized", varName),
                    null
            ));
        }

        return null;
    }
}
