package com.intellij.psi.controlFlow;

import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiVariable;

public interface ControlFlowPolicy {
  PsiVariable getUsedVariable(PsiReferenceExpression refExpr);
  boolean isParameterAccepted(PsiParameter psiParameter);
  boolean isLocalVariableAccepted(PsiLocalVariable psiVariable);
}
