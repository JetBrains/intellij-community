package com.intellij.refactoring.move.moveInstanceMethod;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiVariable;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.ui.UsageViewDescriptorAdapter;
import org.jetbrains.annotations.NotNull;

/**
 * @author dsl
 */
public class MoveInstanceMethodViewDescriptor extends UsageViewDescriptorAdapter {
  private final PsiMethod myMethod;
  private final PsiVariable myTargetVariable;
  private final PsiClass myTargetClass;

  public MoveInstanceMethodViewDescriptor(
    PsiMethod method,
    PsiVariable targetVariable,
    PsiClass targetClass) {
    super();
    myMethod = method;
    myTargetVariable = targetVariable;
    myTargetClass = targetClass;
  }

  @NotNull
  public PsiElement[] getElements() {
    return new PsiElement[] {myMethod, myTargetVariable, myTargetClass};
  }

  public String getProcessedElementsHeader() {
    return RefactoringBundle.message("move.instance.method.elements.header");
  }

}
