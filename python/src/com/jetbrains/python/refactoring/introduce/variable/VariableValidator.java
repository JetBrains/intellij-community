package com.jetbrains.python.refactoring.introduce.variable;

import com.intellij.psi.PsiElement;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.refactoring.introduce.IntroduceValidator;
import org.jetbrains.annotations.Nullable;

/**
 * Created by IntelliJ IDEA.
 * User: Alexey.Ivanov
 * Date: Aug 19, 2009
 * Time: 4:45:40 PM
 */
public class VariableValidator extends IntroduceValidator {
  @Nullable
  public String check(String name, PsiElement psiElement) {
    if (isDefinedInScope(name, psiElement)) {
      return PyBundle.message("refactoring.introduce.variable.scope.error");
    }
    return null;
  }
}
