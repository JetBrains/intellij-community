package com.intellij.refactoring.inheritanceToDelegation;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.ui.UsageViewDescriptorAdapter;
import org.jetbrains.annotations.NotNull;

/**
 * @author dsl
 */
public class InheritanceToDelegationViewDescriptor extends UsageViewDescriptorAdapter {
  private final PsiClass myClass;

  public InheritanceToDelegationViewDescriptor(PsiClass aClass) {
    super();
    myClass = aClass;
  }

  @NotNull
  public PsiElement[] getElements() {
    return new PsiElement[] { myClass };
  }

  public String getProcessedElementsHeader() {
    return RefactoringBundle.message("replace.inheritance.with.delegation.elements.header");
  }
}
