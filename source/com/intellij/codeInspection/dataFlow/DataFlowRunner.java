/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 28, 2002
 * Time: 10:16:39 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInspection.dataFlow.instructions.BinopInstruction;
import com.intellij.codeInspection.dataFlow.instructions.BranchingInstruction;
import com.intellij.codeInspection.dataFlow.instructions.Instruction;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.psi.PsiCodeBlock;

import java.util.ArrayList;
import java.util.EmptyStackException;
import java.util.HashSet;
import java.util.Set;

public class DataFlowRunner {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.dataFlow.DataFlowRunner");
  private static final long ourTimeLimit = 10000;

  private Instruction[] myInstructions;
  private final HashSet myNPEInstructions;
  private DfaVariableValue[] myFields;
  private final HashSet myCCEInstructions;

  public Instruction getInstruction(int index) {
    return myInstructions[index];
  }

  public DataFlowRunner() {
    myNPEInstructions = new HashSet();
    myCCEInstructions = new HashSet();
    DfaValueFactory.freeInstance();
  }

  public boolean analyzeMethod(PsiCodeBlock psiBlock) {
    try {
      ControlFlow flow = new ControlFlowAnalyzer().buildControlFlow(psiBlock);
      if (flow == null) return false;

      myInstructions = flow.getInstructions();
      myFields = flow.getFields();

      if (LOG.isDebugEnabled()) {
        for (int i = 0; i < myInstructions.length; i++) {
          Instruction instruction = myInstructions[i];
          LOG.debug("" + i + ": " + instruction.toString());
        }
      }

      int branchCount = 0;
      for (int i = 0; i < myInstructions.length; i++) {
        Instruction instruction = myInstructions[i];
        if (instruction instanceof BranchingInstruction) branchCount++;
      }

      if (branchCount > 80) return false; // Do not even try. Definetly will out of time.

      final ArrayList queue = new ArrayList();
      queue.add(new DfaInstructionState(myInstructions[0], DfaMemoryStateImpl.createEmpty()));

      final boolean unitTestMode = ApplicationManager.getApplication().isUnitTestMode();
      final long before = System.currentTimeMillis();
      while (queue.size() > 0) {
        if (!unitTestMode && System.currentTimeMillis() - before > ourTimeLimit) return false;
        ProgressManager.getInstance().checkCanceled();

        DfaInstructionState instructionState = (DfaInstructionState)queue.remove(0);
        if (LOG.isDebugEnabled()) {
          LOG.debug(instructionState.toString());
        }

        Instruction instruction = instructionState.getInstruction();
        long distance = instructionState.getDistanceFromStart();

        if (instruction instanceof BranchingInstruction) {
          instruction.setMemoryStateProcessed(instructionState.getMemoryState().createCopy());
        }

        DfaInstructionState[] after = instruction.apply(DataFlowRunner.this, instructionState.getMemoryState());
        if (after != null) {
          for (int i = 0; i < after.length; i++) {
            DfaInstructionState state = after[i];
            Instruction nextInstruction = state.getInstruction();
            if (!(nextInstruction instanceof BranchingInstruction) ||
                !nextInstruction.isMemoryStateProcessed(state.getMemoryState())) {
              state.setDistanceFromStart(distance + 1);
              queue.add(state);
            }
          }
        }
      }

      return true;
    }
    catch (EmptyStackException e) /* TODO[max] !!! hack (of 18186). Please fix in better times. */ {
      return false;
    }
    finally {
      DfaValueFactory.freeInstance();
    }
  }

  public void onInstructionProducesNPE(Instruction instruction) {
    myNPEInstructions.add(instruction);
  }

  public void onInstructionProducesCCE(Instruction instruction) {
    myCCEInstructions.add(instruction);
  }

  public Set getCCEInstructions() {
    return myCCEInstructions;
  }

  public Set getNPEInstructions() {
    return myNPEInstructions;
  }

  public Set getRedundantInstanceofs() {
    HashSet result = new HashSet(1);
    for (int i = 0; i < myInstructions.length; i++) {
      Instruction instruction = myInstructions[i];
      if (instruction instanceof BinopInstruction) {
        if (((BinopInstruction)instruction).isInstanceofRedundant()) {
          result.add(instruction);
        }
      }
    }

    return result;
  }

  public DfaVariableValue[] getFields() {
    return myFields;
  }

  public HashSet[] getConstConditionalExpressions() {
    HashSet trueSet = new HashSet();
    HashSet falseSet = new HashSet();

    for (int i = 0; i < myInstructions.length; i++) {
      Instruction instruction = myInstructions[i];
      if (instruction instanceof BranchingInstruction) {
        BranchingInstruction branchingInstruction = (BranchingInstruction)instruction;
        if (branchingInstruction.getPsiAnchor() != null && branchingInstruction.isConditionConst()) {
          if (!branchingInstruction.isTrueReachable()) {
            falseSet.add(branchingInstruction);
          }

          if (!branchingInstruction.isFalseReachable()) {
            trueSet.add(branchingInstruction);
          }
        }
      }
    }

    for (int i = 0; i < myInstructions.length; i++) {
      Instruction instruction = myInstructions[i];
      if (instruction instanceof BranchingInstruction) {
        BranchingInstruction branchingInstruction = (BranchingInstruction)instruction;
        if (branchingInstruction.isTrueReachable()) {
          falseSet.remove(branchingInstruction);
        }
        if (branchingInstruction.isFalseReachable()) {
          trueSet.remove(branchingInstruction);
        }
      }
    }

    return new HashSet[]{trueSet, falseSet};
  }
}
