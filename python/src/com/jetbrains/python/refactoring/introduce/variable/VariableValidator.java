package com.jetbrains.python.refactoring.introduce.variable;

import com.intellij.lang.LanguageNamesValidation;
import com.intellij.lang.refactoring.NamesValidator;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.refactoring.PyRefactoringUtil;
import com.jetbrains.python.refactoring.introduce.IntroduceValidator;
import com.jetbrains.python.refactoring.introduce.PyIntroduceSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Created by IntelliJ IDEA.
 * User: Alexey.Ivanov
 * Date: Aug 19, 2009
 * Time: 4:45:40 PM
 */
public class VariableValidator implements IntroduceValidator {
  private static final NamesValidator myNamesValidator = LanguageNamesValidation.INSTANCE.forLanguage(PythonLanguage.getInstance());

  public boolean isNameValid(@NotNull PyIntroduceSettings settings) {
    final String name = settings.getName();
    return (name != null) &&
           (myNamesValidator.isIdentifier(name, settings.getProject())) &&
           !(myNamesValidator.isKeyword(name, settings.getProject()));
  }

  @Nullable
  public String check(@NotNull PyIntroduceSettings settings) {
    final String name = settings.getName();
    PsiElement psiElement = settings.getExpression();
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
      return PyBundle.message("refactoring.introduce.variable.scope.error");
    }
    return null;
  }
}
