package com.intellij.lang.java;

import com.intellij.lang.refactoring.RefactoringSupportProvider;
import com.intellij.lang.refactoring.InlineHandler;
import com.intellij.psi.*;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.introduceVariable.IntroduceVariableHandler;
import org.jetbrains.annotations.Nullable;

/**
 * @author ven
 */
public class JavaRefactoringSupportProvier implements RefactoringSupportProvider {
  public boolean isSafeDeleteAvailable(PsiElement element) {
    return element instanceof PsiClass || element instanceof PsiMethod || element instanceof PsiField ||
           (element instanceof PsiParameter && ((PsiParameter)element).getDeclarationScope() instanceof PsiMethod) ||
           element instanceof PsiPackage;
  }

  public RefactoringActionHandler getIntroduceVariableHandler() {
    return new IntroduceVariableHandler();
  }

  @Nullable
  public InlineHandler getInlineHandler() {
    return null;
  }
}
