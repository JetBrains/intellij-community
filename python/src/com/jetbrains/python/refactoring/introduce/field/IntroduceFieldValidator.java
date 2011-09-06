package com.jetbrains.python.refactoring.introduce.field;

import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.refactoring.introduce.IntroduceValidator;

/**
 * @author Dennis.Ushakov
 */
public class IntroduceFieldValidator extends IntroduceValidator {
  @Override
  public String check(String name, PsiElement psiElement) {
    PyClass containingClass = PsiTreeUtil.getParentOfType(psiElement, PyClass.class);
    if (containingClass == null) {
      return null;
    }
    if (containingClass.findInstanceAttribute(name, true) != null) {
      return PyBundle.message("refactoring.introduce.constant.scope.error");
    }
    return null;
  }
}
