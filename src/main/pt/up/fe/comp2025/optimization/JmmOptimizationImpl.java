package pt.up.fe.comp2025.optimization;

import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.ollir.JmmOptimization;
import pt.up.fe.comp.jmm.ollir.OllirResult;

import pt.up.fe.comp2025.ConfigOptions;

import java.util.Collections;

public class JmmOptimizationImpl implements JmmOptimization {

    @Override
    public OllirResult toOllir(JmmSemanticsResult semanticsResult) {

        // Create visitor that will generate the OLLIR code
        var visitor = new OllirGeneratorVisitor(semanticsResult.getSymbolTable());

        // Visit the AST and obtain OLLIR code
        var ollirCode = visitor.visit(semanticsResult.getRootNode());

        //System.out.println("\nOLLIR:\n\n" + ollirCode);

        return new OllirResult(semanticsResult, ollirCode, Collections.emptyList());
    }

    @Override
    public JmmSemanticsResult optimize(JmmSemanticsResult semanticsResult) {
        //TODO: Do your AST-based optimizations here

        // Skip if no config
        if (semanticsResult.getConfig() == null) {
            return semanticsResult;
        }

        // Check for optimization option
        if (semanticsResult.getConfig().containsKey("optimize")) {
            // Instantiate visitors
            ConstantPropagationVisitor propagationVisitor = new ConstantPropagationVisitor();
            ConstantFoldingVisitor foldingVisitor = new ConstantFoldingVisitor();
            do {
                // reset state
                propagationVisitor.resetModified();
                foldingVisitor.resetModified();

                // apply propagation optimization
                try {
                    propagationVisitor.visit(semanticsResult.getRootNode(), semanticsResult.getSymbolTable());
                } catch (Exception e) {
                    e.printStackTrace();
                }

                // apply folding optimization
                try {
                    foldingVisitor.visit(semanticsResult.getRootNode());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            while(propagationVisitor.hasModified() || foldingVisitor.hasModified());
        }
        return semanticsResult;
    }

    @Override
    public OllirResult optimize(OllirResult ollirResult) {

        if (ollirResult.getConfig() == null)
            return ollirResult;

        int registers = ConfigOptions.getRegisterAllocation(ollirResult.getConfig());

        if (registers < 0)
            return ollirResult;

        try {
            new RegisterAllocator(ollirResult, registers).allocate();
        } catch (Exception e) {
            System.err.println("Error while allocating registers: " + e.getMessage());
        }
        return ollirResult;
    }
}
