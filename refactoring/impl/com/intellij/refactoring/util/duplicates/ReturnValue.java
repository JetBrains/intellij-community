/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.refactoring.util.duplicates;

import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiStatement;
import com.intellij.util.IncorrectOperationException;

/**
 * @author dsl
 */
public interface ReturnValue {
  boolean isEquivalent(ReturnValue other);

  PsiStatement createReplacement(PsiMethodCallExpression methodCallExpression) throws IncorrectOperationException;
}
