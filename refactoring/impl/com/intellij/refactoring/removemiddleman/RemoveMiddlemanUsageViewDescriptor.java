package com.intellij.refactoring.removemiddleman;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.refactoring.RefactorJBundle;
import com.intellij.refactoring.base.BaseUsageViewDescriptor;
import com.intellij.refactoring.psi.MyUsageViewUtil;
import com.intellij.usageView.UsageInfo;
import org.jetbrains.annotations.NotNull;

class RemoveMiddlemanUsageViewDescriptor extends BaseUsageViewDescriptor {
    private
    @NotNull
    PsiField field;

    RemoveMiddlemanUsageViewDescriptor(@NotNull PsiField field, UsageInfo[] usages) {
        super(usages);
        this.field = field;
    }

    public String getCodeReferencesText(int usagesCount, int filesCount) {
        return RefactorJBundle.message("references.to.expose.usage.view",
                MyUsageViewUtil.getUsageCountInfo(usagesCount, filesCount, "reference"));
    }

    public String getProcessedElementsHeader() {
        return RefactorJBundle.message("remove.middleman.field.header");
    }

    public PsiElement[] getElements() {
        return new PsiElement[]{field};
    }

}
