package com.jetbrains.python.refactoring;

import com.intellij.lang.refactoring.DefaultRefactoringSupportProvider;
import com.intellij.refactoring.RefactoringActionHandler;
import com.jetbrains.python.refactoring.introduce.constant.ConstantIntroduceHandler;
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
}
