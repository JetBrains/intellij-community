/**
 * @author cdr
 */
package com.intellij.psi.controlFlow;

import com.intellij.psi.PsiElement;

import java.util.List;

public interface ControlFlow {
  ControlFlow EMPTY = new ControlFlowImpl();

  int JUMP_ROLE_GOTO_END = 0;
  int JUMP_ROLE_GOTO_THEN = 1;
  int JUMP_ROLE_GOTO_ELSE = 2;

  Instruction[] getInstructions();

  List<Instruction> getInstructionsList();

  int getSize();

  int getStartOffset(PsiElement element);

  int getEndOffset(PsiElement element);

  PsiElement getElement(int offset);

  // true means there is at least one place where controlflow has been shortcircuited due to constant condition
  // false means no constant conditions were detected affecting control flow
  boolean isConstantConditionOccurred();
}