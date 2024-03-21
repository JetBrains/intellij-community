// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.refactoring;

import com.intellij.lang.refactoring.RefactoringSupportProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.changeSignature.ChangeSignatureHandler;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.refactoring.changeSignature.PyChangeSignatureHandler;
import com.jetbrains.python.refactoring.classes.extractSuperclass.PyExtractSuperclassHandler;
import com.jetbrains.python.refactoring.classes.pullUp.PyPullUpHandler;
import com.jetbrains.python.refactoring.classes.pushDown.PyPushDownHandler;
import com.jetbrains.python.refactoring.extractmethod.PyExtractMethodHandler;
import com.jetbrains.python.refactoring.introduce.constant.PyIntroduceConstantHandler;
import com.jetbrains.python.refactoring.introduce.field.PyIntroduceFieldHandler;
import com.jetbrains.python.refactoring.introduce.parameter.PyIntroduceParameterHandler;
import com.jetbrains.python.refactoring.introduce.variable.PyIntroduceVariableHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class PyRefactoringProvider extends RefactoringSupportProvider {
  @Override
  public RefactoringActionHandler getIntroduceVariableHandler() {
    return new PyIntroduceVariableHandler();
  }

  @Override
  public RefactoringActionHandler getIntroduceConstantHandler() {
    return new PyIntroduceConstantHandler();
  }

  @Override
  public RefactoringActionHandler getIntroduceFieldHandler() {
    return new PyIntroduceFieldHandler();
  }

  @Override
  public RefactoringActionHandler getPullUpHandler() {
    return new PyPullUpHandler();
  }

  @Override
  public RefactoringActionHandler getPushDownHandler() {
    return new PyPushDownHandler();
  }

  @Override
  public RefactoringActionHandler getExtractSuperClassHandler() {
    return new PyExtractSuperclassHandler();
  }

  @Override
  public RefactoringActionHandler getExtractMethodHandler() {
    return new PyExtractMethodHandler();
  }

  @Override
  public boolean isInplaceRenameAvailable(@NotNull PsiElement element, PsiElement context) {
    if (context != null && context.getContainingFile() != element.getContainingFile()) return false;
    PyFunction containingFunction = PsiTreeUtil.getParentOfType(element, PyFunction.class);
    if (containingFunction != null) {
      if (element instanceof PyTargetExpression || element instanceof PyFunction || element instanceof PyClass) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean isMemberInplaceRenameAvailable(@NotNull PsiElement element, @Nullable PsiElement context) {
    return false;
  }

  @Override
  public @Nullable ChangeSignatureHandler getChangeSignatureHandler() {
    return new PyChangeSignatureHandler();
  }

  @Override
  public @Nullable RefactoringActionHandler getIntroduceParameterHandler() {
    return new PyIntroduceParameterHandler();
  }
}
