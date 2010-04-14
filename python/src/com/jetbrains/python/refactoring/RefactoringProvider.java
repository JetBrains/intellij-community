package com.jetbrains.python.refactoring;

import com.intellij.lang.refactoring.DefaultRefactoringSupportProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.RefactoringActionHandler;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.refactoring.classes.extractSuperclass.PyExtractSuperclassHandler;
import com.jetbrains.python.refactoring.classes.pullUp.PyPullUpHandler;
import com.jetbrains.python.refactoring.classes.pushDown.PyPushDownHandler;
import com.jetbrains.python.refactoring.extractmethod.PyExtractMethodHandler;
import com.jetbrains.python.refactoring.introduce.constant.ConstantIntroduceHandler;
import com.jetbrains.python.refactoring.introduce.field.FieldIntroduceHandler;
import com.jetbrains.python.refactoring.introduce.variable.VariableIntroduceHandler;

/**
 * Created by IntelliJ IDEA.
 * User: Alexey.Ivanov
 * Date: Aug 19, 2009
 * Time: 6:26:22 PM
 */
public class RefactoringProvider extends DefaultRefactoringSupportProvider {
  @Override
  public RefactoringActionHandler getIntroduceVariableHandler() {
    return new VariableIntroduceHandler();
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
  public boolean doInplaceRenameFor(PsiElement element, PsiElement context) {
    return (element instanceof PyTargetExpression && PsiTreeUtil.getParentOfType(element, PyFunction.class) != null) ||
           element instanceof PyNamedParameter;
  }
}
