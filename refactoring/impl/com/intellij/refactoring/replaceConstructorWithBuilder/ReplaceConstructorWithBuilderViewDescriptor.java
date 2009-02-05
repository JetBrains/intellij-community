/*
 * User: anna
 * Date: 07-May-2008
 */
package com.intellij.refactoring.replaceConstructorWithBuilder;

import com.intellij.psi.PsiElement;
import com.intellij.refactoring.ui.UsageViewDescriptorAdapter;
import org.jetbrains.annotations.NotNull;

public class ReplaceConstructorWithBuilderViewDescriptor extends UsageViewDescriptorAdapter{

  @NotNull
  public PsiElement[] getElements() {
    return new PsiElement[0];
  }

  public String getProcessedElementsHeader() {
    return "";
  }
}
