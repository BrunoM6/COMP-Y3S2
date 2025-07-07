package pt.up.fe.comp2025.optimization;

import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2025.ast.Kind;
import pt.up.fe.comp2025.ast.TypeUtils;
import pt.up.fe.specs.util.collections.AccumulatorMap;

/**
 * Utility methods related to the optimization middle-end.
 */
public class OptUtils {

    public static final String i32_t = ".i32";
    public static final String bool_t = ".bool";
    public static final String void_t = ".V";

    private final AccumulatorMap<String> temporaries;

    public OptUtils() {
        this.temporaries = new AccumulatorMap<>();
    }

    public String nextTemp() {
        return nextTemp("tmp");
    }

    public String nextTemp(String prefix) {

        // Subtract 1 because the base is 1
        var nextTempNum = temporaries.add(prefix) - 1;

        return prefix + nextTempNum;
    }

    public String toOllirType(JmmNode typeNode) {

        Kind.TYPE.checkOrThrow(typeNode);

        return toOllirType(TypeUtils.convertType(typeNode));
    }

    public String toOllirType(Type type) {
        if (type.isArray())
            return ".array" + toOllirType(type.getName());
        else
            return toOllirType(type.getName());
    }

    public String toOllirType(String typeName) {
        return switch (typeName) {
            case "int" -> i32_t;
            case "void"  -> void_t;
            case "boolean" -> bool_t;
            default -> "." + typeName;
        };
    }
}
