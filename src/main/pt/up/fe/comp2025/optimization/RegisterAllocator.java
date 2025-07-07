package pt.up.fe.comp2025.optimization;

import org.specs.comp.ollir.*;
import org.specs.comp.ollir.inst.*;
import org.specs.comp.ollir.Element;
import org.specs.comp.ollir.Operand;

import org.specs.comp.ollir.type.Type;
import pt.up.fe.comp.jmm.ollir.OllirResult;

import java.util.*;

public class RegisterAllocator {

    private final OllirResult ollirResult;
    private final Integer registerCount;

    private final Set<Element> operands;
    private final Map<Instruction, Set<Element>> liveIn;
    private final Map<Instruction, Set<Element>> liveOut;
    private final Map<Instruction,Set<Element>> used;
    private final Map<Instruction,Set<Element>> defined;
    private final Map<String, Set<String>> edges;
    private Set<Element> savedIn;
    private Set<Element> savedOut;
    private final Map<String,Integer> allocation;
    private int reservedSize = 1;
    private int reservedThis;

    public RegisterAllocator(OllirResult ollirResult, int registerCount) {
        this.ollirResult = ollirResult;
        this.registerCount = registerCount;
        this.operands = new HashSet<>();
        this.liveIn = new HashMap<>();
        this.liveOut = new HashMap<>();
        this.used = new HashMap<>();
        this.defined = new HashMap<>();
        this.edges = new HashMap<>();
        this.savedIn = new HashSet<>();
        this.savedOut = new HashSet<>();
        this.allocation = new HashMap<>();
    }

    public void allocate() {
        for (Method method : ollirResult.getOllirClass().getMethods()) {
            if (method.isConstructMethod()) continue;

            // Build CFG and reset temps
            method.buildCFG();
            reservedThis = method.isStaticMethod() ? 0 : 1;
            reservedSize = reservedThis + method.getParams().size();
            if (method.getInstructions().getFirst().getSuccessors() != null) {
                // Perform all stages
                initializeVariables(method);
                buildOperands(method);
                performLivenessAnalysis(method);
                constructGraph(method);
                colorGraph(method);
                updateVarTable(method);
                operands.clear();
                edges.clear();
                used.clear();
                defined.clear();
                liveIn.clear();
                liveOut.clear();
                allocation.clear();
            }

        }
    }

    private void initializeDef(Instruction topInstr, Instruction current) {
        // Assign instructions -> define
        if (current.getInstType().equals(InstructionType.ASSIGN)) {
            // The LHS of the assign is a new definition
            defined.get(current).add(
                    ((AssignInstruction) current).getDest()
            );

            // Recurse into RHS, since it may be a nested instruction with defines
            initializeDef(topInstr, ((AssignInstruction) current).getRhs());

            // It also may use variables
            initializeUsed(topInstr, ((AssignInstruction) current).getRhs());
        }
    }

    private void initializeUsed(Instruction topInstr, Instruction curr) {
        switch (curr.getInstType()) {
            case ASSIGN:
                // The RHS may be a nested instruction
                initializeUsed(topInstr, ((AssignInstruction) curr).getRhs());
                break;

            case BINARYOPER:
                BinaryOpInstruction bin = (BinaryOpInstruction) curr;
                addUseOperand(topInstr, bin.getLeftOperand());
                addUseOperand(topInstr, bin.getRightOperand());
                break;

            case NOPER:
                SingleOpInstruction uni = (SingleOpInstruction) curr;
                addUseOperand(topInstr, uni.getSingleOperand());
                break;

            case CALL:
                CallInstruction call = (CallInstruction) curr;
                // Regular calls
                for (Element arg : call.getArguments()) {
                    if (arg instanceof Operand)
                        addUseOperand(topInstr, (Operand) arg);
                }
                break;

            case GETFIELD:
                // objectRef.getField()
                GetFieldInstruction gf = (GetFieldInstruction) curr;
                addUseOperand(topInstr, gf.getOperands().get(0));
                break;

            case PUTFIELD:
                PutFieldInstruction pf = (PutFieldInstruction) curr;
                addUseOperand(topInstr, pf.getOperands().get(0));      // object
                if (pf.getOperands().get(1) instanceof Operand)
                    addUseOperand(topInstr, (Operand) pf.getOperands().get(1));  // value
                break;

            case RETURN:
                ReturnInstruction ret = (ReturnInstruction) curr;
                if (ret.getOperand() != null && ret.getOperand().isPresent())
                    addUseOperand(topInstr, ret.getOperand().get());
                break;

            default:
                // nothing to do
        }
    }

    // Helper to only add non-literal Operands
    private void addUseOperand(Instruction topInstr, Element e) {
        if (e instanceof Operand && !((Operand) e).isLiteral()) {
            used.computeIfAbsent(topInstr, k -> new HashSet<>())
                    .add((Operand) e);
        }
    }

    private void buildOperands(Method method) {
        // Defined variables
        for (Set<Element> set : defined.values()) {
            this.operands.addAll(set);
        }
    }

    private void initializeVariables(Method method) {
        for (Instruction instruction : method.getInstructions()) {
            this.liveIn.put(instruction, new HashSet<>());
            this.liveOut.put(instruction, new HashSet<>());
            this.used.put(instruction, new HashSet<>());
            this.defined.put(instruction, new HashSet<>());

            // Recursion used (needed repeated parameters for first call)
            initializeDef(instruction, instruction);
            initializeUsed(instruction, instruction);
        }
    }

    private void performLivenessAnalysis(Method method) {
        // Perform liveness analysis and stop when no changes
        // we never remove vars -> no adding -> done
        boolean changed = true;
        while (changed) {
            changed = false;

            for (Instruction instruction : method.getInstructions()) {
                savedIn = new HashSet<>(liveIn.get(instruction));
                savedOut = new HashSet<>(liveOut.get(instruction));
                for (Node successor : instruction.getSuccessors()) {
                    if (successor instanceof Instruction) {
                        liveOut.get(instruction).addAll(liveIn.get(successor));
                    }
                }

                Set<Element> tempLiveOut = new HashSet<>(savedOut);

                for (Element inst : defined.get(instruction)) {
                    tempLiveOut.removeIf(inst2 -> inst.toString().equals(inst2.toString()));
                }

                liveIn.get(instruction).addAll(tempLiveOut);
                liveIn.get(instruction).addAll(used.get(instruction));

                if (!((savedIn.equals(liveIn.get(instruction)) && savedOut.equals(liveOut.get(instruction))))) {
                    changed = true;
                }
            }
        }
    }

    private void constructGraph(Method method) {
        // First pass: build edges based on def/live‐out
        for (Instruction instr : method.getInstructions()) {
            Set<Element> defs       = defined.getOrDefault(instr, Collections.emptySet());
            Set<Element> liveOutSet = liveOut.getOrDefault(instr, Collections.emptySet());

            for (Element defVar : defs) {
                if (!(defVar instanceof Operand)) continue;
                String dname = ((Operand)defVar).getName();

                // Make sure 'dname' is in the graph even if it ends up with no neighbors
                edges.computeIfAbsent(dname, k -> new HashSet<>());

                for (Element liveVar : liveOutSet) {
                    if (!(liveVar instanceof Operand)) continue;
                    String lname = ((Operand)liveVar).getName();

                    // Skip self‐edges
                    if (dname.equals(lname)) continue;

                    // Add the undirected edge
                    edges.computeIfAbsent(dname, k -> new HashSet<>()).add(lname);
                    edges.computeIfAbsent(lname, k -> new HashSet<>()).add(dname);
                }
            }
        }

        // Second pass: make sure every local var shows up as a node
        // (so you don’t lose isolated temporaries like tmp0 when they never interfere)
        for (Operand paramOrLocal : method.getVarTable().keySet().stream()
                .map(name -> new Operand(name, null))
                .toList()) {
            String name = paramOrLocal.getName();
            edges.putIfAbsent(name, new HashSet<>());
        }
    }

    private void colorGraph(Method method) {
        // Build the set of names we skip (this + parameters)
        Set<String> reserved = new HashSet<>();
        if (reservedThis == 1) {
            reserved.add("this");
        }
        for (Element p : method.getParams()) {
            reserved.add(((Operand)p).getName());
        }

        // Build a working copy of the graph *only* over locals
        Map<String, Set<String>> graphCopy = new HashMap<>();
        for (String var : edges.keySet()) {
            if (reserved.contains(var)) continue;
            Set<String> nbrs = new HashSet<>(edges.getOrDefault(var, Collections.emptySet()));
            nbrs.removeAll(reserved);
            graphCopy.put(var, nbrs);
        }

        // Get the number of colors used
        int availableColors = (registerCount == 0) ? graphCopy.size() : registerCount;

        if (availableColors <= 0) {
            throw new RuntimeException("Not enough registers");
        }

        // Simplify: peel off any node with degree < k
        Deque<String> stack = new ArrayDeque<>();
        while (!graphCopy.isEmpty()) {
            boolean found = false;
            for (Iterator<Map.Entry<String,Set<String>>> it = graphCopy.entrySet().iterator(); it.hasNext();) {
                var ent = it.next();
                if (ent.getValue().size() < availableColors) {
                    // Safe to remove
                    String node = ent.getKey();
                    stack.push(node);
                    it.remove();
                    // Drop this node from all other adjacency lists
                    for (Set<String> other : graphCopy.values()) {
                        other.remove(node);
                    }
                    found = true;
                    break;
                }
            }
            if (!found) {
                // No node of degree<k => algorithm cannot proceed
                throw new RuntimeException("Register allocation failed: graph has a node of degree ≥ " + availableColors);
            }
        }

        // Select: pop and assign the lowest free color
        while (!stack.isEmpty()) {
            String node = stack.pop();
            Set<Integer> usedRegs = new HashSet<>();
            for (String nb : edges.getOrDefault(node, Collections.emptySet())) {
                Integer c = allocation.get(nb);
                if (c != null) usedRegs.add(c);
            }
            // Pick first color not in usedRegs
            int reg = 0;
            while (reg < availableColors && usedRegs.contains(reg)) {
                reg++;
            }
            if (reg >= availableColors) {
                throw new RuntimeException("Unexpected: no available register for " + node);
            }
            allocation.put(node, reg);
        }
    }

    private void updateVarTable(Method method) {
        Map<String,Descriptor> vt = method.getVarTable();

        // `this` goes in reg 0 (if instance)
        if (reservedThis == 1) {
            vt.get("this").setVirtualReg(0);
        }

        // Parameters
        for (int i = 0; i < method.getParams().size(); i++) {
            String name = ((Operand)method.getParams().get(i)).getName();
            vt.get(name).setVirtualReg(reservedThis + i);
        }

        // Locals
        for (Map.Entry<String,Integer> e : allocation.entrySet()) {
            String name = e.getKey();
            int    c    = e.getValue();
            Descriptor d = vt.get(name);

            // Find the descriptor
            if (d != null) {
                d.setVirtualReg(c + reservedSize);
            }
        }
    }
}
