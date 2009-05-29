package com.intellij.psi.controlFlow;

import com.intellij.psi.PsiExpression;
import org.jetbrains.annotations.NonNls;


public class ConditionalGoToInstruction extends ConditionalBranchingInstruction {
  public final boolean isReturn = false; //true if goto has been generated as a result of return statement

  public ConditionalGoToInstruction(int offset, final PsiExpression expression) {
    this(offset, Role.END, expression);
  }
  public ConditionalGoToInstruction(int offset, Role role, final PsiExpression expression) {
    super(offset, expression, role);
  }

  public String toString() {
    final @NonNls String sRole = "["+role.toString()+"]";
    return "COND_GOTO " + sRole + " " + offset;
  }

  public void accept(ControlFlowInstructionVisitor visitor, int offset, int nextOffset) {
    visitor.visitConditionalGoToInstruction(this, offset, nextOffset);
  }
}
