/*
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Apr 9, 2002
 * Time: 10:27:17 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInspection.dataFlow.instructions;

import com.intellij.codeInspection.dataFlow.DataFlowRunner;
import com.intellij.codeInspection.dataFlow.DfaInstructionState;
import com.intellij.codeInspection.dataFlow.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.value.*;
import com.intellij.codeInspection.redundantCast.RedundantCastUtil;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeCastExpression;

public class TypeCastInstruction extends Instruction {
  private final PsiTypeCastExpression myCastExpression;
  private final DfaRelationValue myInstanceofRelation;
  private final boolean myIsSemantical;

  public static TypeCastInstruction createInstruction(PsiTypeCastExpression castExpression) {
    PsiExpression expr = castExpression.getOperand();
    PsiType castType = castExpression.getCastType().getType();

    if (expr == null || castType == null) return null;

    if (RedundantCastUtil.isTypeCastSemantical(castExpression)) {
      return new TypeCastInstruction();
    }

    DfaValue dfaExpr = DfaValueFactory.create(expr);
    DfaTypeValue dfaType = DfaTypeValue.Factory.getInstance().create(castType);
    if (dfaExpr == null) return null;

    DfaRelationValue dfaInstanceof = DfaRelationValue.Factory.getInstance().create(dfaExpr, dfaType, "instanceof", false);
    return dfaInstanceof != null ? new TypeCastInstruction(castExpression, dfaInstanceof) : null;
  }

  public TypeCastInstruction() {
    myCastExpression = null;
    myInstanceofRelation = null;
    myIsSemantical = true;
  }

  private TypeCastInstruction(PsiTypeCastExpression castExpression, DfaRelationValue instanceofRelation) {
    myIsSemantical = false;
    myCastExpression = castExpression;
    myInstanceofRelation = instanceofRelation;
  }

  public DfaInstructionState[] apply(DataFlowRunner runner, DfaMemoryState memState) {
    if (myIsSemantical) {
      memState.pop();
      memState.push(DfaUnknownValue.getInstance());
    }
    else if (!memState.applyInstanceofOrNull(myInstanceofRelation)) {
      runner.onInstructionProducesCCE(this);
    }

    return new DfaInstructionState[] {new DfaInstructionState(runner.getInstruction(getIndex() + 1), memState)};
  }

  public PsiTypeCastExpression getCastExpression() {
    return myCastExpression;
  }
}
