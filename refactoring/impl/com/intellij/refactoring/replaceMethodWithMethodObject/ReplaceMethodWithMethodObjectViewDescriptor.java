/*
 * User: anna
 * Date: 06-May-2008
 */
package com.intellij.refactoring.replaceMethodWithMethodObject;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.usageView.UsageViewDescriptor;
import org.jetbrains.annotations.NotNull;

public class ReplaceMethodWithMethodObjectViewDescriptor implements UsageViewDescriptor {
  private final PsiMethod myMethod;

  public ReplaceMethodWithMethodObjectViewDescriptor(final PsiMethod method) {
    myMethod = method;
  }

  @NotNull
  public PsiElement[] getElements() {
    return new PsiElement[]{myMethod};
  }

  public String getProcessedElementsHeader() {
    return "Method to be converted";
  }

  public String getCodeReferencesText(final int usagesCount, final int filesCount) {
    return "References to be changed";
  }

  public String getCommentReferencesText(final int usagesCount, final int filesCount) {
    return null;
  }
}