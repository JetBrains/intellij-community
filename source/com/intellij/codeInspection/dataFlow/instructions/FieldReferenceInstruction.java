package com.intellij.codeInspection.dataFlow.instructions;

import com.intellij.codeInspection.dataFlow.DataFlowRunner;
import com.intellij.codeInspection.dataFlow.DfaInstructionState;
import com.intellij.codeInspection.dataFlow.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.value.*;
import com.intellij.psi.PsiArrayAccessExpression;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiVariable;

/**
 * @author max
 */
public class FieldReferenceInstruction extends Instruction {
  private PsiExpression myExpression;
  private boolean myIsPhysical;

  public FieldReferenceInstruction(PsiReferenceExpression expression) {
    myExpression = expression;
    myIsPhysical = expression.isPhysical();
  }

  public FieldReferenceInstruction(PsiArrayAccessExpression expression) {
    myExpression = expression;
  }

  public DfaInstructionState[] apply(DataFlowRunner runner, DfaMemoryState memState) {
    final DfaValue qualifier = memState.pop();
    if (myIsPhysical && !memState.applyNotNull(qualifier)) {
      runner.onInstructionProducesNPE(this);
      return new DfaInstructionState[0];
    }

    return new DfaInstructionState[]{new DfaInstructionState(runner.getInstruction(getIndex() + 1), memState)};
  }

  public String toString() {
    return "FIELD_REFERENCE: " + myExpression.getText();
  }

  public PsiExpression getExpression() { return myExpression; }
}
