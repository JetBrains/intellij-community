// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.refactoring.introduce.constant;

import com.intellij.psi.PsiElement;
import com.jetbrains.python.PyPsiBundle;
import com.jetbrains.python.refactoring.introduce.IntroduceValidator;

/**
 * @author Alexey.Ivanov
 */
public class ConstantValidator extends IntroduceValidator {
  @Override
  public String check(String name, PsiElement psiElement) {
    if (isDefinedInScope(name, psiElement) || isDefinedInScope(name, psiElement.getContainingFile())) {
      return PyPsiBundle.message("refactoring.introduce.constant.scope.error");
    }
    return null;
  }
}
