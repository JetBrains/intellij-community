package com.jetbrains.python.refactoring.introduce.constant;

import com.intellij.psi.PsiElement;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.refactoring.introduce.IntroduceValidator;

/**
 * @author Alexey.Ivanov
 */
public class ConstantValidator extends IntroduceValidator {
  protected String simpleCheck(String name, PsiElement psiElement) {
    if (isDefinedInScope(name, psiElement)) {
      return PyBundle.message("refactoring.introduce.constant.scope.error");
    }
    return null;
  }
}
