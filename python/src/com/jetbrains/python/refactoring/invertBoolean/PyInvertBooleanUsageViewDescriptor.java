package com.jetbrains.python.refactoring.invertBoolean;

import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.usageView.UsageViewUtil;
import org.jetbrains.annotations.NotNull;

/**
 * User : ktisha
 */
public class PyInvertBooleanUsageViewDescriptor implements UsageViewDescriptor {
  private final PsiElement myElement;

  public PyInvertBooleanUsageViewDescriptor(final PsiElement element) {
    myElement = element;
  }

  @NotNull
  public PsiElement[] getElements() {
    return new PsiElement[] {myElement};
  }

  public String getProcessedElementsHeader() {
    return RefactoringBundle.message("invert.boolean.elements.header", UsageViewUtil.getType(myElement));
  }

  public String getCodeReferencesText(int usagesCount, int filesCount) {
    return RefactoringBundle.message("invert.boolean.refs.to.invert", UsageViewBundle.getReferencesString(usagesCount, filesCount));
  }

  public String getCommentReferencesText(int usagesCount, int filesCount) {
    return null;
  }
}
