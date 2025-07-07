package pt.up.fe.comp2025.backend;

import org.specs.comp.ollir.*;
import org.specs.comp.ollir.inst.*;
import org.specs.comp.ollir.tree.TreeNode;
import org.specs.comp.ollir.type.ArrayType;
import org.specs.comp.ollir.type.BuiltinKind;
import org.specs.comp.ollir.type.BuiltinType;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.specs.util.classmap.FunctionClassMap;
import pt.up.fe.specs.util.exceptions.NotImplementedException;
import pt.up.fe.specs.util.utilities.StringLines;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.lang.Integer;

/**
 * Generates Jasmin code from an OllirResult.
 * <p>
 * One JasminGenerator instance per OllirResult.
 */
public class JasminGenerator {

    private static final String NL = "\n";
    private static final String TAB = "   ";
    private static final String SPACE = " ";

    private final OllirResult ollirResult;

    List<Report> reports;

    String code;

    Method currentMethod;

    int ifcmpLabel;

    private final JasminUtils utils;

    private final FunctionClassMap<TreeNode, String> generators;

    private int currentStack;
    private int maxStack;

    public JasminGenerator(OllirResult ollirResult) {
        this.ollirResult = ollirResult;

        reports = new ArrayList<>();
        code = null;
        currentMethod = null;

        utils = new JasminUtils(ollirResult);

        this.generators = new FunctionClassMap<>();
        generators.put(ClassUnit.class, this::generateClassUnit);
        generators.put(Field.class, this::generateField);
        generators.put(Method.class, this::generateMethod);
        generators.put(AssignInstruction.class, this::generateAssign);
        generators.put(SingleOpInstruction.class, this::generateSingleOp);
        generators.put(LiteralElement.class, this::generateLiteral);
        generators.put(Operand.class, this::generateOperand);
        generators.put(BinaryOpInstruction.class, this::generateBinaryOp);
        generators.put(ReturnInstruction.class, this::generateReturn);
        generators.put(InvokeStaticInstruction.class, this::generateCall);
        generators.put(InvokeVirtualInstruction.class, this::generateCall);
        generators.put(PutFieldInstruction.class, this::generateField);
        generators.put(GetFieldInstruction.class, this::generateField);
        generators.put(InvokeSpecialInstruction.class, this::generateInvokeSpecial);
        generators.put(NewInstruction.class, this::generateNew);
        generators.put(CondBranchInstruction.class, this::generateCondBranch);
        generators.put(GotoInstruction.class, this::generateGoto);
        generators.put(ArrayLengthInstruction.class, this::generateArrayLength);
        generators.put(UnaryOpInstruction.class, this::generateUnaryOp);
    }

    private String apply(TreeNode node) {

        // Print the corresponding OLLIR code as a comment
        //code.append("; ").append(node).append(NL);

        return generators.apply(node);
    }

    public List<Report> getReports() {
        return reports;
    }

    public String build() {

        // This way, build is idempotent
        if (code == null) {
            code = apply(ollirResult.getOllirClass());
        }

        return code;
    }

    private String generateClassUnit(ClassUnit classUnit) {

        var code = new StringBuilder();

        // generate class name
        var className = ollirResult.getOllirClass().getClassName();
        code.append(".class ").append(className).append(NL).append(NL);

        var fullSuperClass = utils.getSuperPath();
        code.append(".super ").append(fullSuperClass).append(NL);

        // generate code for fields
        for (var field : ollirResult.getOllirClass().getFields())
            code.append(apply(field));

        code.append(NL);

        // generate a single constructor method
        var defaultConstructor = """
                ;default constructor
                .method public <init>()V
                    aload_0
                    invokespecial %s/<init>()V
                    return
                .end method
                """.formatted(fullSuperClass);
        code.append(defaultConstructor);

        // generate code for all other methods
        for (var method : ollirResult.getOllirClass().getMethods()) {

            // Ignore constructor, since there is always one constructor
            // that receives no arguments, and has been already added
            // previously
            if (method.isConstructMethod()) {
                continue;
            }

            code.append(apply(method));
        }

        return code.toString();
    }

    private String generateField(Field field) {
        return NL +
                ".field public '" +
                field.getFieldName() +
                "' " +
                utils.getType(field.getFieldType());
    }

    private void updateStack(int delta) {
        currentStack += delta;
        if (currentStack > maxStack) {
            maxStack = currentStack;
        }
        if (currentStack < 0) {
            throw new RuntimeException("Stack underflow detected");
        }
    }

    private void emitWithStackTracking(String instruction) {
        // Calculate stack effect for this specific instruction
        int delta = getInstructionStackDelta(instruction);
        updateStack(delta);
    }

    private int getInstructionStackDelta(String instruction) {
        String opcode = instruction.trim().split("\\s+")[0];

        return switch (opcode) {
            // Stack pushers (+1)
            case "aload_0", "aload_1", "aload_2", "aload_3" -> 1;
            case "iload_0", "iload_1", "iload_2", "iload_3" -> 1;
            case "aload", "iload" -> 1;
            case "iconst_0", "iconst_1", "iconst_2", "iconst_3", "iconst_4", "iconst_5" -> 1;
            case "iconst_m1" -> 1;
            case "bipush", "sipush" -> 1;
            case "ldc" -> 1;
            case "new" -> 1;
            case "dup" -> 1;
            case "arraylength" -> 0; // pops array, pushes length

            // Stack poppers (-1)
            case "istore_0", "istore_1", "istore_2", "istore_3" -> -1;
            case "astore_0", "astore_1", "astore_2", "astore_3" -> -1;
            case "istore", "astore" -> -1;
            case "pop" -> -1;
            case "ireturn", "areturn" -> -1;
            case "return" -> 0;

            // Binary operations (-1, pops 2 pushes 1)
            case "iadd", "isub", "imul", "idiv" -> -1;
            case "ixor" -> -1;

            // Array operations
            case "newarray", "anewarray" -> 0; // pops size, pushes array
            case "iaload", "aaload" -> -1; // pops array+index, pushes value
            case "iastore", "aastore" -> -3; // pops array+index+value

            // Method calls - need special handling
            case "invokevirtual", "invokestatic", "invokespecial" ->
                    calculateMethodCallDelta(instruction);

            // Field operations
            case "getfield" -> 0; // pops object, pushes field value
            case "putfield" -> -2; // pops object+value

            // Comparisons and branches
            case "if_icmpeq", "if_icmpne", "if_icmplt", "if_icmpge", "if_icmpgt", "if_icmple" -> -2;
            case "ifeq", "ifne", "iflt", "ifge", "ifgt", "ifle" -> -1;
            case "ifnull", "ifnonnull" -> -1;
            case "goto" -> 0;

            // Increment
            case "iinc" -> 0;

            default -> 0; // Conservative default
        };
    }

    private int calculateMethodCallDelta(String instruction) {
        // Parse method signature to determine stack effect
        // Format: invokevirtual Class/method(params)returnType

        try {
            String[] parts = instruction.split("\\s+");
            if (parts.length < 2) return 0;

            String methodSig = parts[1];
            int parenStart = methodSig.indexOf('(');
            int parenEnd = methodSig.indexOf(')');

            if (parenStart == -1 || parenEnd == -1) return 0;

            String params = methodSig.substring(parenStart + 1, parenEnd);
            String returnType = methodSig.substring(parenEnd + 1);

            // Count parameters (simplified - doesn't handle complex types perfectly)
            int paramCount = 0;
            for (int i = 0; i < params.length(); i++) {
                char c = params.charAt(i);
                if (c == 'I' || c == 'Z' || c == 'L') { // int, boolean, object
                    paramCount++;
                    if (c == 'L') {
                        // Skip to semicolon for object types
                        while (i < params.length() && params.charAt(i) != ';') i++;
                    }
                }
            }

            // Add 1 for object reference (for non-static calls)
            if (instruction.startsWith("invokevirtual") || instruction.startsWith("invokespecial")) {
                paramCount++; // object reference
            }

            // Determine return type effect
            int returnEffect = returnType.equals("V") ? 0 : 1;

            return returnEffect - paramCount;

        } catch (Exception e) {
            return 0; // Conservative fallback
        }
    }

    private String generateMethod(Method method) {

        // set method
        currentMethod = method;
        ifcmpLabel = 0;

        var code = new StringBuilder();
        var body = new StringBuilder();

        // calculate modifier
        var modifier = utils.getModifier(method.getMethodAccessModifier());

        var methodName = method.getMethodName();

        // no need for separate apply method
        String params = method.getParams().stream()
                .map(e -> utils.getType(e.getType()))
                .collect(Collectors.joining());

        String returnType = utils.getType(method.getReturnType());

        code.append("\n.method ").
                append(modifier);

        if (method.isStaticMethod())
            code.append("static")
                    .append(SPACE);

        code.append(methodName)
                .append("(")
                .append(params)
                .append(")")
                .append(returnType)
                .append(NL);

        // Add limits
        int localsLimit = 1 + method.getVarTable().values().stream()
                .mapToInt(Descriptor::getVirtualReg)
                .max()
                .orElseThrow();

        maxStack = 0;
        currentStack = 0;

        for (var inst : method.getInstructions()) {
            var instCode = apply(inst);

            String[] lines = instCode.split("\n");
            for (String line : lines) {
                if (!line.trim().isEmpty() && !line.trim().startsWith(";")) {
                    emitWithStackTracking(line.trim());
                }
            }

            var _instCode = StringLines.getLines(instCode).stream()
                    .collect(Collectors.joining(NL + TAB, TAB, NL));

            var labelsCode = method.getLabels(inst).stream()
                    .map(label -> label + ":" + NL)
                    .collect(Collectors.joining());

            body.append(labelsCode);
            body.append(_instCode);
        }

        code.append(TAB)
                .append(".limit stack ")
                .append(maxStack)
                .append(NL);

        code.append(TAB)
                .append(".limit locals ")
                .append(localsLimit)
                .append(NL);

        code.append(body);

        code.append(".end method\n");

        // unset method
        currentMethod = null;

        return code.toString();
    }

    private String generateAssign(AssignInstruction assign) {
        var code = new StringBuilder();

        if (!(assign.getDest() instanceof Operand lhs))
            // todo: ???
            throw new NotImplementedException(assign.getDest().getClass());

        // get register
        var reg = currentMethod.getVarTable().get(lhs.getName());

        // try to optimize
        var litStr = getIincLit(lhs, assign.getRhs());
        if (litStr != null)
            return code.append("iinc ")
                    .append(reg.getVirtualReg())
                    .append(SPACE)
                    .append(litStr)
                    .toString();

        boolean isArrayOp = lhs instanceof ArrayOperand;
        if (isArrayOp)
            // load array + index
            code.append(apply(assign.getDest()));

        // load RHS
        code.append(apply(assign.getRhs()));

        // store value
        code.append(utils.getTypePrefix(lhs.getType()));

        if (isArrayOp)
            // store array value
            code.append("astore");
        else {
            code.append("store");

            // store_x optimization
            if (reg.getVirtualReg() <= 3)
                code.append("_");
            else
                code.append(" ");

            code.append(reg.getVirtualReg());
        }

        return code.append(NL)
                .toString();
    }

    // Check if iinc optimization is available; if so, return the literal
    private String getIincLit(Operand lhs, Instruction rhs) {
        if (!(rhs instanceof BinaryOpInstruction biop)) // invalid inst
            return null;

        var opType = biop.getOperation().getOpType();
        var isAddOp = opType.equals(OperationType.ADD);
        var isSubOp = opType.equals(OperationType.SUB);

        if (!isAddOp && !isSubOp) // invalid op
            return null;

        var leftOp = biop.getLeftOperand();
        var rightOp = biop.getRightOperand();
        String litStr = null;

        if (leftOp instanceof Operand tmp &&
                tmp.getName().equals(lhs.getName()) &&
                rightOp instanceof LiteralElement lit) {
            litStr = lit.getLiteral();
            if (isSubOp)
                litStr = "-" + litStr;
        }

        else if (rightOp instanceof Operand tmp &&
                tmp.getName().equals(lhs.getName()) &&
                leftOp instanceof LiteralElement lit) {
            if (isSubOp) // cannot optimize in form lit - var
                return null;
            litStr = lit.getLiteral();
        }

        if (litStr == null)
            return null;

        int litVal = Integer.parseInt(litStr);

        if (litVal < -128 || litVal > 127)
            // only bytes are allowed
            return null;

        return litStr;
    }

    private String generateSingleOp(SingleOpInstruction singleOp) {
        var operand = singleOp.getSingleOperand();

        String code = apply(singleOp.getSingleOperand());

        // Special case- load array value
        if (operand instanceof ArrayOperand) {
            var prefix = utils.getTypePrefix(operand.getType());
            code += prefix + "aload" + NL;
        }

        return code;
    }

    private String generateLiteral(LiteralElement literal) {

        String prefix = "ldc "; // default

        if (literal.getType() instanceof BuiltinType builtinType) {
            if (builtinType.getKind().equals(BuiltinKind.BOOLEAN))
                prefix = "iconst_"; // 0 or 1

            else if (builtinType.getKind().equals(BuiltinKind.INT32)) {
                int lit = Integer.parseInt(literal.getLiteral());
                if (lit >= 0 && lit <= 5)
                    // Negative ints are not supported in JMM!
                    prefix = "iconst_";

                else if (lit >= -128 && lit <= 127)
                    prefix = "bipush "; // b for byte

                else if (lit >= -32768 && lit <= 32767)
                    prefix = "sipush "; // s for short
            }
        }

        return prefix + literal.getLiteral() + NL;
    }

    private String generateOperand(Operand operand) {
        StringBuilder code = new StringBuilder();

        // get register
        var reg = currentMethod.getVarTable().get(operand.getName());

        boolean isArrayOp = operand instanceof ArrayOperand;

        if (isArrayOp)
            // load array reference (not array value)
            code.append("aload");
        else
            code.append(utils.getTypePrefix(operand.getType()))
                    .append("load");

        // optimize if reg <=3
        if (reg.getVirtualReg() <= 3)
            code.append("_");
        else
            code.append(" ");

        code.append(reg.getVirtualReg());
        code.append(NL);

        if (isArrayOp)
            // load array index
            code.append(apply(((ArrayOperand)operand).getIndexOperands().getFirst()));

        return code.toString();
    }

    private String generateBinaryOp(BinaryOpInstruction binaryOp) {
        var code = new StringBuilder();

        String tmp = getCmpZeroCode(binaryOp);

        if (tmp == null) {
            // no optimization available
            code.append(apply(binaryOp.getLeftOperand()));
            code.append(apply(binaryOp.getRightOperand()));
        }
        else
            // includes apply code for the non-0 operand
            // + optimized instruction
            code.append(tmp);

        // Always i (Jmm doesn't support double or float)
        var typePrefix = "i";
        var opType = binaryOp.getOperation().getOpType();

        String op = switch (opType) {
            case ADD -> "add";
            case SUB -> "sub";
            case MUL -> "mul";
            case DIV -> "div";
            case LTH -> "_lt";
            case LTE -> "_le";
            case GTH -> "_gt";
            case GTE -> "_ge";
            case EQ -> "_eq";
            case NEQ -> "_neq";
            default -> throw new IllegalArgumentException("Unsupported binary op");
        };

        // Special binary op, requires new labels
        if (op.startsWith("_")) {
            int labelIdx = ifcmpLabel++;
            String trueLabel = "j_true_" + labelIdx;
            String endLabel = "j_end" + labelIdx;

            if (tmp == null)
                code.append("if_icmp")
                        .append(op.substring(1));
            // else-appended earlier

            code.append(SPACE)
                    .append(trueLabel)
                    .append(NL)
                    .append("iconst_0\n")
                    .append("goto ")
                    .append(endLabel)
                    .append(NL)
                    // insert TRUE label
                    .append(trueLabel)
                    .append(":\n")
                    .append("iconst_1\n")
                    // insert END label
                    .append(endLabel)
                    .append(":\n");
        }

        else code.append(typePrefix)
                .append(op)
                .append(NL);

        return code.toString();
    }

    // Check if it is possible to optimize using if<cond> i.e.,
    // int comparison with zero; if so, return the comparison code
    private String getCmpZeroCode(BinaryOpInstruction biOp) {
        var lhs = biOp.getLeftOperand();
        var rhs = biOp.getRightOperand();
        var opType = biOp.getOperation().getOpType();

        boolean zeroOnRight = true;

        if (lhs instanceof LiteralElement litLhs
                && litLhs.getLiteral().equals("0"))
            zeroOnRight = false;

        else if (!(rhs instanceof LiteralElement litRhs
                && litRhs.getLiteral().equals("0")))
            return null; // not applicable

        // add code for non-0 operand
        String operCode = apply(zeroOnRight ? lhs : rhs);

        // optimized instruction
        String optCode = switch (opType) {
            case EQ -> "ifeq";
            case NEQ -> "ifneq";
            case LTH -> zeroOnRight ? "iflt" : "ifgt";
            case GTH -> zeroOnRight ? "ifgt" : "iflt";
            case LTE -> zeroOnRight ? "ifle" : "ifge";
            case GTE -> zeroOnRight ? "ifge" : "ifle";
            default -> null; // not a valid opType
        };

        if (optCode == null)
            return null;
        return operCode + optCode;
    }

    private String generateReturn(ReturnInstruction returnInst) {
        var code = new StringBuilder();

        // Load return value onto stack (if applicable)
        var operand = returnInst.getOperand();
        operand.ifPresent(element -> code.append(apply(element)));

        var returnType = returnInst.getReturnType();

        code.append(utils.getTypePrefix(returnType))
                .append("return")
                .append(NL);

        return code.toString();
    }

    private String generateCall(CallInstruction inst) {
        StringBuilder code = new StringBuilder();

        String mname = ((LiteralElement)inst.getMethodName()).getLiteral();
        String prefix;

        if (inst instanceof InvokeStaticInstruction) {
            prefix = "invokestatic " +
                    utils.getClassPath(((Operand)inst.getCaller()).getName());
        }
        else if (inst instanceof InvokeVirtualInstruction) {
            code.append(apply(inst.getCaller())); // load caller

            prefix = "invokevirtual " +
                    utils.getClassPath(inst.getCaller().getType());
        }
        else
            throw new IllegalStateException("InvokeSpecial not supported here");

        for (var arg : inst.getArguments())
            code.append(apply(arg)); // load args

        code.append(prefix)
                .append("/")
                .append(mname)
                .append("(");

        code.append(
                inst.getArguments().stream()
                .map(e -> utils.getType(e.getType()))
                .collect(Collectors.joining())
        );

        code.append(")")
                .append(utils.getType(inst.getReturnType()))
                .append(NL);
        return code.toString();
    }

    private String generateField(FieldInstruction inst) {
        StringBuilder code = new StringBuilder();

        // load class instance (always 'this' in Jmm)
        code.append(apply(inst.getObject()));

        String fname = inst.getField().getName();
        String ftype = utils.getType(inst.getField().getType());
        String fclass = ollirResult.getOllirClass().getClassName();

        if (inst instanceof PutFieldInstruction) {
            var value = inst.getOperands().get(2);
            code.append(apply(value)) // load value
                    .append("putfield ");

        } else // GetFieldInstruction
            code.append("getfield ");

        code.append(fclass)
                .append("/")
                .append(fname)
                .append(SPACE)
                .append(ftype)
                .append(NL);

        return code.toString();
    }

    private String generateNew(NewInstruction newInst) {
        StringBuilder code = new StringBuilder();

        String _class = ((Operand)newInst.getCaller()).getName();

        if (_class.equals("array")) {
            // new array[...]
            var arrayE = newInst.getOperands().get(0);
            var arraySizeE = newInst.getOperands().get(1);

            // visit arraySize element
            code.append(apply(arraySizeE));

            String arrayTypecode = "";
            var aType = ((ArrayType)arrayE.getType()).getElementType();

            if (aType instanceof BuiltinType biType) {
                switch (biType.getKind()) {
                    //case VOID
                    case INT32 -> arrayTypecode = "int";
                    case BOOLEAN -> arrayTypecode = "boolean";
                    case STRING -> arrayTypecode = "java/lang/String";
                }
            }
            else arrayTypecode = utils.getClassPath(aType);

            String p = utils.getTypePrefix(aType);
            code.append(p.equals("a") ? "anewarray " : "newarray ")
                    .append(arrayTypecode)
                    .append(NL);
        }
        else {
            // new Object()
            code.append("new ")
                    .append(utils.getClassPath(_class))
                    .append(NL);
        }

        return code.toString();
    }

    private String generateInvokeSpecial(InvokeSpecialInstruction inst) {
        StringBuilder code = new StringBuilder();

        // Load object onto the stack
        var obj = inst.getOperands().getFirst();
        var objCode = apply(obj);
        code.append(objCode);

        String _class = utils.getClassPath(inst.getCaller().getType());

        code.append("invokenonvirtual ")
                .append(_class)
                .append("/")
                // User-defined constructors not supported in JMM
                // so it's ok to hardcode for now
                .append("<init>()V")
                .append(NL);

        return code.toString();
    }

    private String generateCondBranch(CondBranchInstruction inst) {
        return apply(inst.getCondition()) +
                "ifne " +
                inst.getLabel();
    }

    private String generateGoto(GotoInstruction gotoInst) {
        return "goto " +
                gotoInst.getLabel();
    }

    private String generateArrayLength(ArrayLengthInstruction inst) {
        return apply(inst.getOperands().getFirst())
                + "arraylength"
                + NL;
    }

    private String generateUnaryOp(UnaryOpInstruction unaryOp) {
        return apply(unaryOp.getOperand()) +
                "iconst_1" +
                NL +
                "ixor" +
                NL;
    }
}
