package com.jetbrains.python.refactoring.introduce.constant;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.refactoring.PyRefactoringUtil;
import com.jetbrains.python.refactoring.introduce.IntroduceValidator;

/**
 * Created by IntelliJ IDEA.
 * User: Alexey.Ivanov
 * Date: Aug 25, 2009
 * Time: 8:29:16 PM
 */
public class ConstantValidator extends IntroduceValidator {
  protected String simpleCheck(String name, PsiElement psiElement) {
    if (psiElement.getUserData(PyPsiUtils.SELECTION_BREAKS_AST_NODE) != null) {
      final Pair<PsiElement,TextRange> data = psiElement.getUserData(PyPsiUtils.SELECTION_BREAKS_AST_NODE);
      psiElement = data.first;
    }
    PsiElement context = PsiTreeUtil.getParentOfType(psiElement, PyFunction.class);
    if (context == null) {
      context = PsiTreeUtil.getParentOfType(psiElement, PyClass.class);
    }
    if (context == null) {
      context = psiElement.getContainingFile();
    }

    if (PyRefactoringUtil.collectScopeVariables(context).contains(name)) {
      return PyBundle.message("refactoring.introduce.constant.scope.error");
    }
    return null;
  }
}
