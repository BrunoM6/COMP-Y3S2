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
import pt.up.fe.comp2025.symboltable.JmmSymbolTable;

import java.util.List;

public class IncompatibleArgs extends AnalysisVisitor {

    private final TypeUtils typeUtils;

    public IncompatibleArgs(SymbolTable symbolTable) {
        this.typeUtils = new TypeUtils(symbolTable, getReports());
    }

    @Override
    public void buildVisitor() {
        addVisit(Kind.METHOD_CALL, this::handleMethodCall);
    }

    //> When calling methods of the class declared in the code, verify if the types
    // of arguments of the call are compatible with the types in the method declaration

    private Void handleMethodCall(JmmNode callExpr, SymbolTable table) {

        String methodName = callExpr.get("name");
        Type mreturnType = typeUtils.getExprType(callExpr);

        // Invalid method; not declared and not imported
        if (mreturnType == null) {
            String message = String.format(
                    "Call to undefined method '%s'",
                    methodName
            );
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    callExpr.getLine(),
                    callExpr.getColumn(),
                    message,
                    null)
            );
            return null;
        }

        // Imported method; cannot check params
        if (TypeUtils.isTypeUnknown(mreturnType)) {
            return null;
        }

        // Call to static method using 'this'
        if (((JmmSymbolTable)table).isMethodStatic(methodName)
                && Kind.THIS_REF.check(callExpr.getChild(0))) {
            String message = String.format(
                    "Call to static method '%s' using 'this'",
                    methodName
            );
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    callExpr.getLine(),
                    callExpr.getColumn(),
                    message,
                    null)
            );
            return null;
        }

        List<Symbol> params = table.getParameters(methodName);
        List<JmmNode> argNodes = callExpr.getChildren(Kind.EXPR)
                .subList(1, callExpr.getChildren(Kind.EXPR).size());

        int i;
        boolean varargMode = false;

        // Compare arguments and method parameters
        for (i=0; i < params.size(); i++) {

            String paramName = params.get(i).getName();
            Type paramType = params.get(i).getType();

            // Too few args
            if (i >= argNodes.size()) {
                String message = String.format(
                        "Too few arguments in call to method '%s': expected %s but got %s",
                        methodName,
                        params.size(),
                        i
                );
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        callExpr.getLine(),
                        callExpr.getColumn(),
                        message,
                        null)
                );
                return null;
            }

            JmmNode argNode = argNodes.get(i);
            Type argType = typeUtils.getExprType(argNode);

            // Vararg
            if (TypeUtils.isVararg(paramType)) {
                // Allow to pass array instead of multiple args
                // todo: allow empty array?
                if (!(argType.isArray() && argType.getName().equals(paramType.getName()))) {
                    varargMode = true;
                }
                else i++;
                break;
            }

            // Invalid arg type
            if (!typeUtils.isAssignableAs(argType, paramType)) {
                String message = String.format(
                        "Invalid call to method '%s': expected '%s' but got '%s' for parameter '%s'",
                        methodName,
                        paramType.print(),
                        argType.print(),
                        paramName
                );
                addReport(Report.newError(
                        Stage.SEMANTIC,
                        argNode.getLine(),
                        argNode.getColumn(),
                        message,
                        null)
                );
                return null;
            }
        }

        if (varargMode) {
            Symbol vararg = params.get(i);
            Type varargType = vararg.getType();

            for (; i < argNodes.size(); i++) {
                JmmNode argNode = argNodes.get(i);
                Type argType = typeUtils.getExprType(argNode);

                // Arg does not match vararg param type
                // todo: check compatibility, not equality??
                if (argType.isArray() || !argType.getName().equals(varargType.getName())) {
                    String message = String.format(
                            "Invalid call to method '%s': expected '%s' but got '%s' for vararg '%s'",
                            methodName,
                            varargType.getName(),
                            argType.print(),
                            vararg.getName()
                    );
                    addReport(Report.newError(
                            Stage.SEMANTIC,
                            argNode.getLine(),
                            argNode.getColumn(),
                            message,
                            null)
                    );
                }
            }
        }

        // Too many args
        else if (i < argNodes.size()) {
            String message = String.format(
                    "Too many arguments in call to method '%s': expected %s but got %s",
                    methodName,
                    params.size(),
                    argNodes.size()
            );
            addReport(Report.newError(
                    Stage.SEMANTIC,
                    callExpr.getLine(),
                    callExpr.getColumn(),
                    message,
                    null)
            );
            return null;
        }

        return null;
    }
}
