package pt.up.fe.comp2025.symboltable;

import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.Type;

import java.util.*;

public class JmmSymbolTable extends AJmmSymbolTable {

    private final String className;
    private final String superClassName;
    private final List<String> imports;
    private final List<Symbol> fields;
    private final Map<String, Type> returnTypes;
    private final Map<String, List<Symbol>> params;
    private final Map<String, List<Symbol>> locals;

    // methodName -> isStatic
    private final Map<String, Boolean> methods;

    public JmmSymbolTable(String className,
                          String superClassName,
                          List<String> imports,
                          List<Symbol> fields,
                          Map<String, Boolean> methods,
                          Map<String, Type> returnTypes,
                          Map<String, List<Symbol>> params,
                          Map<String, List<Symbol>> locals) {

        this.className = className;
        this.superClassName = superClassName;
        this.imports = imports;
        this.fields = fields;
        this.methods = methods;
        this.returnTypes = returnTypes;
        this.params = params;
        this.locals = locals;
    }

    @Override
    public List<String> getImports() {
        return imports;
    }

    @Override
    public String getClassName() {
        return className;
    }

    @Override
    public String getSuper() {
        return superClassName;
    }

    @Override
    public List<Symbol> getFields() {
        return fields;
    }

    @Override
    public List<String> getMethods() {
        // Some overhead over a regular List,
        // but this shouldn't be invoked too often anyway
        return methods.keySet().stream().toList();
    }

    public boolean isMethodStatic(String methodName) {
        return methods.getOrDefault(methodName, false);
    }

    @Override
    public Type getReturnType(String methodSignature) {
        return returnTypes.get(methodSignature);
    }

    @Override
    public List<Symbol> getParameters(String methodSignature) {
        return params.get(methodSignature);
    }

    @Override
    public List<Symbol> getLocalVariables(String methodSignature) {
        return locals.get(methodSignature);
    }

    @Override
    public String toString() {
        return print();
    }
}
