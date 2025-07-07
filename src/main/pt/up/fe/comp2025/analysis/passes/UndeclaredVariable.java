package pt.up.fe.comp2025.analysis.passes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.analysis.AnalysisVisitor;
import pt.up.fe.comp2025.ast.Kind;
import pt.up.fe.comp2025.ast.TypeUtils;
import pt.up.fe.specs.util.SpecsCheck;

import java.util.ArrayList;
import java.util.List;

public class UndeclaredVariable extends AnalysisVisitor {

    private String currentMethod;
    private final TypeUtils typeUtils;

    public UndeclaredVariable(SymbolTable symbolTable) {
        this.typeUtils = new TypeUtils(symbolTable, getReports());
    }

    @Override
    public void buildVisitor() {
        addVisit(Kind.METHOD_DECL, this::visitMethodDecl);
        addVisit(Kind.VAR_REF_EXPR, this::visitVarRefExpr);
        addVisit(Kind.VAR_DECL, this::visitVarDecl);
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        currentMethod = method.get("name");
        return null;
    }

    private Void visitVarRefExpr(JmmNode varRefExpr, SymbolTable table) {

        SpecsCheck.checkNotNull(currentMethod, () -> "Expected current method to be set");

        Type varType = typeUtils.getExprType(varRefExpr);
        if (varType != null) {
            // Var is defined
            return null;
        }

        String varName = varRefExpr.get("name");
        boolean classRef = false;
        String message;

        // reference to our class
        if (varName.equals(table.getClassName())) {
            classRef = true;
        }
        // reference to imported class
        else for (String _import : table.getImports()) {
            if (_import.equals(varName)) {
                classRef = true;
                break;
            }
        }

        if (classRef) {
            // Exception for static references inside method calls
            JmmNode parent = varRefExpr.getParent();
            if (Kind.METHOD_CALL.check(parent) && parent.getChild(0).equals(varRefExpr)) {
                return null;
            }
            else {
                // Referenced class outside method call; not allowed
                message = String.format(
                        "Reference to class '%s' can only be used to invoke static methods",
                        varName
                );
            }
        }
        else message = String.format("Reference to variable '%s' does not exist", varName);

        addReport(Report.newError(
                Stage.SEMANTIC,
                varRefExpr.getLine(),
                varRefExpr.getColumn(),
                message,
                null)
        );

        return null;
    }

    private Void visitVarDecl(JmmNode varDecl, SymbolTable table) {

        Type varType = TypeUtils.convertType(varDecl.getChild(0));
        String typeName = varType.getName();

        // Cannot declare void variables
        if(typeName.equals("void")) {
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    varDecl.getLine(),
                    varDecl.getColumn(),
                    "Cannot declare void variables",
                    null)
            );
            return null;
        }

        List<String> imports = table.getImports();

        if (!TypeUtils.isPrimitiveType(varType)
                && !typeName.equals(table.getClassName())
                && !imports.contains(typeName)) {
            // Type not imported
            String message = String.format(
                    "Unknown type '%s' of variable '%s' (forgot to import?)",
                    typeName,
                    varDecl.get("name")
            );
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    varDecl.getLine(),
                    varDecl.getColumn(),
                    message,
                    null)
            );
            return null;
        }

        return null;
    }
}
