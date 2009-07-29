package com.intellij.lang.java;

import com.intellij.lang.refactoring.RefactoringSupportProvider;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.memberPushDown.JavaPushDownHandler;
import com.intellij.refactoring.memberPullUp.JavaPullUpHandler;
import com.intellij.refactoring.introduceParameter.IntroduceParameterHandler;
import com.intellij.refactoring.extractMethod.ExtractMethodHandler;
import com.intellij.refactoring.introduceField.IntroduceConstantHandler;
import com.intellij.refactoring.introduceField.IntroduceFieldHandler;
import com.intellij.refactoring.introduceVariable.IntroduceVariableHandler;
import org.jetbrains.annotations.Nullable;

/**
 * @author ven
 */
public class JavaRefactoringSupportProvider implements RefactoringSupportProvider {
  public boolean isSafeDeleteAvailable(PsiElement element) {
    return element instanceof PsiClass || element instanceof PsiMethod || element instanceof PsiField ||
           (element instanceof PsiParameter && ((PsiParameter)element).getDeclarationScope() instanceof PsiMethod) ||
           element instanceof PsiPackage;
  }

  public RefactoringActionHandler getIntroduceConstantHandler() {
    return new IntroduceConstantHandler();
  }

  public RefactoringActionHandler getIntroduceFieldHandler() {
    return new IntroduceFieldHandler();
  }

  public boolean doInplaceRenameFor(final PsiElement element, final PsiElement context) {
    return mayRenameInplace(element, context);
  }

  public RefactoringActionHandler getIntroduceVariableHandler() {
    return new IntroduceVariableHandler();
  }

  @Nullable
  public RefactoringActionHandler getExtractMethodHandler() {
    return new ExtractMethodHandler();
  }

  public RefactoringActionHandler getIntroduceParameterHandler() {
    return new IntroduceParameterHandler();
  }

  public RefactoringActionHandler getPullUpHandler() {
    return new JavaPullUpHandler();
  }

  public RefactoringActionHandler getPushDownHandler() {
    return new JavaPushDownHandler();
  }

  public static boolean mayRenameInplace(PsiElement elementToRename, final PsiElement nameSuggestionContext) {
    if (!(elementToRename instanceof PsiVariable)) return false;
    if (nameSuggestionContext != null && nameSuggestionContext.getContainingFile() != elementToRename.getContainingFile()) return false;
    if (!(elementToRename instanceof PsiLocalVariable) && !(elementToRename instanceof PsiParameter)) return false;
    SearchScope useScope = elementToRename.getUseScope();
    if (!(useScope instanceof LocalSearchScope)) return false;
    PsiElement[] scopeElements = ((LocalSearchScope) useScope).getScope();
    if (scopeElements.length > 1) return false; //assume there are no elements with use scopes with holes in'em
    PsiFile containingFile = elementToRename.getContainingFile();
    return PsiTreeUtil.isAncestor(containingFile, scopeElements[0], false);
  }
}
