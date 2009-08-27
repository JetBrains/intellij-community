package com.jetbrains.python.refactoring.introduce;

import com.intellij.lang.LanguageNamesValidation;
import com.intellij.lang.refactoring.NamesValidator;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.PyExpression;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Created by IntelliJ IDEA.
 * User: Alexey.Ivanov
 * Date: Aug 19, 2009
 * Time: 4:20:13 PM
 */
public abstract class IntroduceValidator {
  private final NamesValidator myNamesValidator = LanguageNamesValidation.INSTANCE.forLanguage(PythonLanguage.getInstance());

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
    return simpleCheck(name, psiElement);
  }

  public boolean checkPossibleName(@NotNull final String name, @NotNull final PyExpression expression) {
    return simpleCheck(name, expression) == null;
  }

  @Nullable
  protected abstract String simpleCheck(String name, PsiElement psiElement);
}
