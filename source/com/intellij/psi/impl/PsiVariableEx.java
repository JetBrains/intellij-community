package com.intellij.psi.impl;

import com.intellij.psi.*;

import java.util.Set;

public interface PsiVariableEx extends PsiVariable{
  Object computeConstantValue(Set<PsiVariable> visitedVars);
}
