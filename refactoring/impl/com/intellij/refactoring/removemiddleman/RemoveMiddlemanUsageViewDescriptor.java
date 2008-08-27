package com.intellij.refactoring.removemiddleman;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.refactoring.RefactorJBundle;
import com.intellij.refactoring.psi.MyUsageViewUtil;
import com.intellij.usageView.UsageViewDescriptor;
import org.jetbrains.annotations.NotNull;

class RemoveMiddlemanUsageViewDescriptor implements UsageViewDescriptor {
  private @NotNull PsiField field;

  RemoveMiddlemanUsageViewDescriptor(@NotNull PsiField field) {
    super();
    this.field = field;
  }

  public String getCodeReferencesText(int usagesCount, int filesCount) {
    return RefactorJBundle
      .message("references.to.expose.usage.view", MyUsageViewUtil.getUsageCountInfo(usagesCount, filesCount, "reference"));
  }

  public String getProcessedElementsHeader() {
    return RefactorJBundle.message("remove.middleman.field.header");
  }

  public PsiElement[] getElements() {
    return new PsiElement[]{field};
  }

  public String getCommentReferencesText(int usagesCount, int filesCount) {
    return null;
  }
}
