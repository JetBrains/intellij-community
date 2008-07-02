package com.intellij.psi.controlFlow;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiExpression;

/**
 * Author: msk
 */
public abstract class ConditionalBranchingInstruction extends BranchingInstruction {
  protected static final Logger LOG = Logger.getInstance("#com.intellij.psi.controlFlow.ConditionalGoToInstruction");
  public final PsiExpression expression;

  public ConditionalBranchingInstruction(int offset, final PsiExpression expression) {
    super(offset);
    this.expression = expression;
  }

  public int nNext() { return 2; }

  public int getNext(int index, int no) {
    switch (no) {
      case 0: return offset;
      case 1: return index + 1;
      default:
        LOG.assertTrue (false);
        return -1;
    }
  }

  public void accept(ControlFlowInstructionVisitor visitor, int offset, int nextOffset) {
    visitor.visitConditionalBranchingInstruction(this, offset, nextOffset);
  }
}
