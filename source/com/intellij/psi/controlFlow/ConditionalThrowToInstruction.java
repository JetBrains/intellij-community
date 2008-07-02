package com.intellij.psi.controlFlow;

import com.intellij.psi.PsiExpression;

public class ConditionalThrowToInstruction extends ConditionalBranchingInstruction {

  public ConditionalThrowToInstruction(int offset, PsiExpression expression) {
    super(offset, expression);
  }

  public ConditionalThrowToInstruction(final int offset) {
    this(offset, null);
  }

  public String toString() {
    return "COND_THROW_TO " + offset;
  }

  public void accept(ControlFlowInstructionVisitor visitor, int offset, int nextOffset) {
    visitor.visitConditionalThrowToInstruction(this, offset, nextOffset);
  }
}
