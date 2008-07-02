package com.intellij.psi.controlFlow;

import com.intellij.psi.PsiExpression;
import org.jetbrains.annotations.NonNls;


public class ConditionalGoToInstruction extends ConditionalBranchingInstruction {

  public final int role;
  public final boolean isReturn; //true if goto has been generated as a result of return statement

  public ConditionalGoToInstruction(int offset, final PsiExpression expression) {
    this(offset,ControlFlow.JUMP_ROLE_GOTO_END, expression);
  }
  public ConditionalGoToInstruction(int offset, int role, final PsiExpression expression) {
    this(offset,role, false, expression);
  }
  public ConditionalGoToInstruction(int offset, int role, boolean isReturn, final PsiExpression expression) {
    super(offset, expression);
    this.role = role;
    this.isReturn = isReturn;
  }

  public String toString() {
    final @NonNls String sRole;
    if (role == ControlFlow.JUMP_ROLE_GOTO_ELSE) sRole = "[ELSE]";
    else if (role == ControlFlow.JUMP_ROLE_GOTO_THEN) sRole = "[THEN]";
    else if (role == ControlFlow.JUMP_ROLE_GOTO_END) sRole = "[END]";
    else {
      LOG.assertTrue(false,"Unknown Role: "+role);
      sRole = "???";
    }

    return "COND_GOTO " + sRole + " " + offset + (isReturn ? " RETURN" : "");
  }

  public void accept(ControlFlowInstructionVisitor visitor, int offset, int nextOffset) {
    visitor.visitConditionalGoToInstruction(this, offset, nextOffset);
  }
}
