package pt.up.fe.comp2025.backend;

import org.specs.comp.ollir.*;
import org.specs.comp.ollir.inst.AssignInstruction;
import org.specs.comp.ollir.type.*;
import org.specs.comp.ollir.inst.*;
import pt.up.fe.comp.jmm.ollir.OllirResult;

public class JasminUtils {

    private final OllirResult ollirResult;

    public JasminUtils(OllirResult ollirResult) {
        // Can be useful to have if you expand this class with more methods
        this.ollirResult = ollirResult;
    }

    public String getModifier(AccessModifier accessModifier) {
        return accessModifier != AccessModifier.DEFAULT ?
                accessModifier.name().toLowerCase() + " " :
                "";
    }

    // Get full path of a class (in Jasmin style)
    public String getClassPath(String className) {
        if (className.equals(ollirResult.getOllirClass().getClassName()))
            return className;

        for (String i : ollirResult.getOllirClass().getImports()) {
            if (i.equals(className) || i.endsWith("." + className))
                return i.replace(".", "/");
        }

        throw new IllegalStateException("Cannot find class path");
    }

    public String getClassPath(Type classType) {
        if (classType instanceof ClassType) {
            return getClassPath(((ClassType) classType).getName());
        }

        throw new IllegalStateException("Type is not a ClassType, does not have an import");
    }

    // Get full path of superclass
    public String getSuperPath() {
        String _super = ollirResult.getOllirClass().getSuperClass();
        if (_super == null || _super.isEmpty())
            return "java/lang/Object";

        return getClassPath(_super);
    }

    public String getType(Type type) {
        if (type instanceof ArrayType arrayType) {
            return "[" + getType(arrayType.getElementType());
        }
        if (type instanceof ClassType classType) {
            return "L" + getClassPath(classType.getName()) + ";";
        }
        if (type instanceof BuiltinType builtinType) {
            return switch (builtinType.getKind()) {
                case VOID -> "V";
                case INT32 -> "I";
                case BOOLEAN -> "Z";
                case STRING -> "Ljava/lang/String;";
            };
        }

        throw new IllegalArgumentException("Unsupported type?");
    }

    public String getTypePrefix(Type type) {
        if (type instanceof BuiltinType biType) {
            return switch (biType.getKind()) {
                case INT32, BOOLEAN -> "i";
                case STRING -> "a";
                case VOID -> "";
            };
        }
        return "a";
    }
}
