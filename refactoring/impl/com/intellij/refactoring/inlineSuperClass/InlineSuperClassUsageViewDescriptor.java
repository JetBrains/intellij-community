/*
 * User: anna
 * Date: 27-Aug-2008
 */
package com.intellij.refactoring.inlineSuperClass;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.ui.UsageViewDescriptorAdapter;
import org.jetbrains.annotations.NotNull;

public class InlineSuperClassUsageViewDescriptor extends UsageViewDescriptorAdapter{
  private PsiClass myClass;

  public InlineSuperClassUsageViewDescriptor(final PsiClass aClass) {
    myClass = aClass;
  }

  @NotNull
  public PsiElement[] getElements() {
    return new PsiElement[] {myClass};
  }

  public String getProcessedElementsHeader() {
    return null;
  }
}