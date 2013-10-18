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
import com.jetbrains.python.refactoring.introduce.parameter.PyIntroduceParameterHandler;
import com.jetbrains.python.refactoring.introduce.constant.PyIntroduceConstantHandler;
import com.jetbrains.python.refactoring.introduce.field.PyIntroduceFieldHandler;
import com.jetbrains.python.refactoring.introduce.variable.PyIntroduceVariableHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Alexey.Ivanov
 */
public class PyRefactoringProvider extends RefactoringSupportProvider {
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

  @Nullable
  @Override
  public ChangeSignatureHandler getChangeSignatureHandler() {
    return new PyChangeSignatureHandler();
  }

  @Nullable
  @Override
  public RefactoringActionHandler getIntroduceParameterHandler() {
    return new PyIntroduceParameterHandler();
  }
}
