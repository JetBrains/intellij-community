/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Jan 26, 2002
 * Time: 10:48:52 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.dataFlow.instructions;

import com.intellij.codeInspection.dataFlow.DataFlowRunner;
import com.intellij.codeInspection.dataFlow.DfaInstructionState;
import com.intellij.codeInspection.dataFlow.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.value.DfaConstValue;
import com.intellij.codeInspection.dataFlow.value.DfaRelationValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiVariable;


public class MethodCallInstruction extends Instruction {
  private final PsiMethodCallExpression myCall;
  private DfaRelationValue myDfaNotNull;

  public MethodCallInstruction(PsiMethodCallExpression call) {
    myCall = call;
    myDfaNotNull = null;
    PsiExpression qualifierExpression = call.getMethodExpression().getQualifierExpression();
    if (qualifierExpression instanceof PsiReferenceExpression) {
      PsiReferenceExpression expression = (PsiReferenceExpression)qualifierExpression;
      PsiVariable psiVariable = DfaValueFactory.resolveVariable(expression);
      if (psiVariable != null) {
        DfaVariableValue dfaVariable = DfaVariableValue.Factory.getInstance().create(psiVariable, false);
        DfaConstValue dfaNull = DfaConstValue.Factory.getInstance().getNull();
        myDfaNotNull = DfaRelationValue.Factory.getInstance().create(dfaVariable, dfaNull, "==", true);
      }
    }
  }

  public DfaInstructionState[] apply(DataFlowRunner runner, DfaMemoryState memState) {
    try {
      if (myDfaNotNull != null && !memState.applyCondition(myDfaNotNull)) {
        runner.onInstructionProducesNPE(this);
        return new DfaInstructionState[0];
      }

      return new DfaInstructionState[]{new DfaInstructionState(runner.getInstruction(getIndex() + 1), memState)};
    }
    finally {
      memState.flushFields(runner);
    }
  }

  public PsiMethodCallExpression getCallExpression() {
    return myCall;
  }

  public String toString() {
    return "CALL_METHOD: " + myCall.getText();
  }
}
