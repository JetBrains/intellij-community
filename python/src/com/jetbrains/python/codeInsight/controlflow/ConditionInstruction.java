package com.jetbrains.python.codeInsight.controlflow;

import com.intellij.psi.PsiElement;

/**
 * @author oleg
 */
public interface ConditionInstruction extends Instruction {
  boolean getResult();
  PsiElement getCondition();
}
