package pt.up.fe.comp2025.symboltable;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2025.ast.TypeUtils;
import pt.up.fe.specs.util.SpecsCheck;

import java.util.*;
import java.util.stream.Collectors;

import static pt.up.fe.comp2025.ast.Kind.*;

public class JmmSymbolTableBuilder {

    private final List<Report> reports = new ArrayList<>();

    public List<Report> getReports() {
        return reports;
    }

    private static Report newError(JmmNode node, String message) {
        return Report.newError(
                Stage.SEMANTIC,
                node.getLine(),
                node.getColumn(),
                message,
                null);
    }

    public JmmSymbolTable build(JmmNode root) {

        var classDecl = root.getChildren(CLASS_DECL).getFirst();
        SpecsCheck.checkNotNull(classDecl, () -> "Expected a class declaration, but found none");

        String className = classDecl.get("name");

        var imports = buildImports(root);
        var fields = buildFields(classDecl);
        var methods = buildMethods(classDecl);
        var params = buildParams(classDecl);
        var locals = buildLocals(classDecl, params);
        var returnTypes = buildReturnTypes(classDecl);

        String superName = buildSuperClassName(classDecl, imports);

        markMainMethod(classDecl, returnTypes, params);

        return new JmmSymbolTable(
                className,
                superName,
                imports,
                fields,
                methods,
                returnTypes,
                params,
                locals
        );
    }

    private String buildSuperClassName(JmmNode classDecl, List<String> imports) {
        if (classDecl.hasAttribute("superName")) {
            String superName = classDecl.get("superName");

            // Superclass import not declared
            if (!imports.contains(superName)) {
                String message = String.format(
                        "Cannot find import for superclass '%s'",
                        superName
                );
                reports.add(newError(classDecl, message));
            }
            return superName;
        }
        return "";
    }

    // Handles nested imports
    // 'import A.c' returns c as the imported class
    private List<String> buildImports(JmmNode program) {

        var importDecls = program.getChildren(IMPORT_DECL);

        // Checks for duplicates + builds the list in 1 go

        HashMap<String, String> seen = new HashMap<>();
        List<String> imports = new ArrayList<>();

        for (var _import : importDecls) {
            String className = (String) _import.getObjectAsList("pkgName").getLast();
            String pkgName = _import.getObjectAsList("pkgName").stream()
                    .map(Object::toString)
                    .collect(Collectors.joining("."));

            if (className.equals("String")) {
                // import String not allowed
                // cannot distinguish from built-in String
                String message = String.format(
                        "Import '%s' is not allowed (clashes with built-in String)",
                        pkgName
                );
                reports.add(newError(_import, message));
            }

            if (seen.containsKey(className)) {
                // Duplicate import
                if (seen.get(className).equals(pkgName)) {
                    String message = String.format(
                            "Duplicate import '%s'",
                            pkgName
                    );
                    reports.add(Report.newWarn(
                            Stage.SEMANTIC,
                            _import.getLine(),
                            _import.getColumn(),
                            message,
                            null)
                    );
                }

                // Ambiguous import
                else {
                    String message = String.format(
                            "Ambiguous import '%s' as '%s' and '%s'",
                            className,
                            seen.get(className),
                            pkgName
                    );
                    reports.add(newError(_import, message));
                }
            }
            else imports.add(className);
            seen.put(className, pkgName);
        }

        return imports;
    }

    private List<Symbol> buildFields(JmmNode classDecl) {

        var varDecls = classDecl.getChildren(VAR_DECL);

        Set<String> seen = new HashSet<>();
        List<Symbol> fieldList = new ArrayList<>();

        for (var field : varDecls) {
            String name = field.get("name");

            // Duplicate field
            if (!seen.add(name)) {
                String message = String.format("Duplicate field '%s'", name);
                reports.add(newError(field, message));
            }

            JmmNode typeNode = field.getChildren().getFirst();
            Type type = TypeUtils.convertType(typeNode);

            // Field of type vararg, throw error
            if (TypeUtils.isVararg(type)) {
                String message = String.format(
                        "Field '%s' of type vararg is not allowed",
                        name
                );
                reports.add(newError(field, message));
            }

            fieldList.add(new Symbol(type, name));
        }

        return fieldList;
    }

    private Map<String, Boolean> buildMethods(JmmNode classDecl) {

        List<JmmNode> methodDecls = classDecl.getChildren(METHOD_DECL);

        Set<String> seen = new HashSet<>();
        Map<String, Boolean> map = new HashMap<>();

        for (var method : methodDecls) {
            String name = method.get("name");
            // Duplicate method (attempting to overload)
            if (!seen.add(name.toLowerCase())) {
                String message = String.format(
                        "Duplicate method '%s': method overloading is not allowed",
                        name
                );
                reports.add(newError(method, message));
            }

            map.put(name, (Boolean)method.getObject("isStatic"));
        }
        return map;
    }

    private Map<String, Type> buildReturnTypes(JmmNode classDecl) {

        Map<String, Type> map = new HashMap<>();

        for (var method : classDecl.getChildren(METHOD_DECL)) {
            String name = method.get("name");
            Type returnType;

            JmmNode typeNode = method.getChildren(TYPE).getFirst();
            returnType = TypeUtils.convertType(typeNode);

            // Return type is vararg, throw error
            if (TypeUtils.isVararg(returnType)) {
                String message = String.format(
                        "Vararg not allowed as a return type in method '%s'",
                        name
                );
                reports.add(newError(method, message));
            }

            map.put(name, returnType);
        }
        return map;
    }

    private Map<String, List<Symbol>> buildParams(JmmNode classDecl) {

        Map<String, List<Symbol>> map = new HashMap<>();

        for (var method : classDecl.getChildren(METHOD_DECL)) {

            var methodName = method.get("name");
            List<Symbol> paramList = new ArrayList<>();

            Set<String> seen = new HashSet<>();
            int paramSize = method.getChildren(PARAM).size();

            for (int i = 0; i < paramSize; i++) {
                JmmNode param = method.getChildren(PARAM).get(i);
                String name = param.get("name");

                // Duplicate param name
                if (!seen.add(name)) {
                    String message = String.format(
                            "Duplicate parameter '%s' in method '%s'",
                            name,
                            methodName
                    );
                    reports.add(newError(method, message));
                }

                JmmNode typeNode = param.getChildren().getFirst();
                Type type = TypeUtils.convertType(typeNode);

                // Vararg is not the last param, error
                // also works for checking if there's >1 vararg
                if (TypeUtils.isVararg(type) && i < paramSize-1) {
                    String message = String.format(
                            "Vararg '%s' in method '%s' must be the last parameter",
                            name,
                            methodName
                    );
                    reports.add(newError(method, message));
                }
                paramList.add(new Symbol(type, name));
            }
            map.put(methodName, paramList);
        }
        return map;
    }

    private Map<String, List<Symbol>> buildLocals(JmmNode classDecl,
                                                  Map<String, List<Symbol>> params) {

        var map = new HashMap<String, List<Symbol>>();

        for (var method : classDecl.getChildren(METHOD_DECL)) {

            var methodName = method.get("name");
            Set<String> seen = new HashSet<>();
            List<Symbol> locals = new ArrayList<>();

            for (JmmNode varDecl : method.getChildren(VAR_DECL)) {
                String varName = varDecl.get("name");

                // Local and param with same name, conflict
                for (var param : params.get(methodName)) {
                    if (param.getName().equals(varName)) {
                        String message = String.format(
                                "Variable named '%s' conflicts with parameter '%s' in method '%s'",
                                varName,
                                varName,
                                methodName
                        );
                        reports.add(newError(varDecl, message));
                    }
                    break;
                }

                // Duplicate local
                if (!seen.add(varName)) {
                    String message = String.format(
                            "Duplicate declaration of variable '%s' in method '%s'",
                            varName,
                            methodName
                    );
                    reports.add(newError(varDecl, message));
                }

                JmmNode typeNode = varDecl.getChildren(TYPE).getFirst();
                Type type = TypeUtils.convertType(typeNode);

                // Local of type vararg
                if (TypeUtils.isVararg(type)) {
                    String message = String.format(
                            "Variable '%s' of type vararg is not allowed",
                            varName
                    );
                    reports.add(newError(varDecl, message));
                }

                locals.add(new Symbol(type, varName));
            }

            map.put(methodName, locals);
        }

        return map;
    }

    private void markMainMethod(JmmNode classDecl,
                                Map<String, Type> returnTypes,
                                Map<String, List<Symbol>> params) {
        for (var method : classDecl.getChildren(METHOD_DECL)) {
            String name = method.get("name");

            // Check if static
            if (name.equals("main")
                    && (Boolean)method.getObject("isStatic")) {

                // Check return type
                if (returnTypes.get(name).equals(TypeUtils.newVoidType())) {

                    // Check params
                    var _params = params.get(name);
                    if (_params.size() == 1) {
                        Type type = _params.getFirst().getType();
                        if (type.isArray() && type.getName().equals("String"))
                            method.putObject("isMain", true);
                    }
                }
            }
        }
    }
}
