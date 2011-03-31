package com.jetbrains.python.refactoring;

import com.intellij.lang.refactoring.RefactoringSupportProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.RefactoringActionHandler;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.refactoring.classes.extractSuperclass.PyExtractSuperclassHandler;
import com.jetbrains.python.refactoring.classes.pullUp.PyPullUpHandler;
import com.jetbrains.python.refactoring.classes.pushDown.PyPushDownHandler;
import com.jetbrains.python.refactoring.extractmethod.PyExtractMethodHandler;
import com.jetbrains.python.refactoring.introduce.constant.ConstantIntroduceHandler;
import com.jetbrains.python.refactoring.introduce.field.FieldIntroduceHandler;
import com.jetbrains.python.refactoring.introduce.variable.PyIntroduceVariableHandler;

/**
 * @author Alexey.Ivanov
 */
public class RefactoringProvider extends RefactoringSupportProvider {
  @Override
  public RefactoringActionHandler getIntroduceVariableHandler() {
    return new PyIntroduceVariableHandler();
  }

  @Override
  public RefactoringActionHandler getIntroduceConstantHandler() {
    return new ConstantIntroduceHandler();
  }

  @Override
  public RefactoringActionHandler getIntroduceFieldHandler() {
    return new FieldIntroduceHandler();
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
  public boolean isInplaceRenameAvailable(PsiElement element, PsiElement context) {
    PyFunction containingFunction = PsiTreeUtil.getParentOfType(element, PyFunction.class);
    if (containingFunction != null) {
      if (element instanceof PyTargetExpression || element instanceof PyFunction || element instanceof PyClass) {
        return true;
      }
      if (element instanceof PyNamedParameter) {
        return containingFunction.getContainingClass() == null;
      }
    }
    return false;
  }
}
