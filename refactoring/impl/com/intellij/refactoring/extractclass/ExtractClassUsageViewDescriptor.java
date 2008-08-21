package com.intellij.refactoring.extractclass;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactorJBundle;
import com.intellij.refactoring.base.BaseUsageViewDescriptor;
import com.intellij.refactoring.psi.MyUsageViewUtil;
import com.intellij.usageView.UsageInfo;

class ExtractClassUsageViewDescriptor extends BaseUsageViewDescriptor {
    private PsiClass aClass;

    ExtractClassUsageViewDescriptor(PsiClass aClass, UsageInfo[] usages) {
        super(usages);
        this.aClass = aClass;
    }


    public String getCodeReferencesText(int usagesCount, int filesCount) {
        return RefactorJBundle.message("references.to.extract") + MyUsageViewUtil.getUsageCountInfo(usagesCount, filesCount, "reference");
    }

    public String getProcessedElementsHeader() {
        return RefactorJBundle.message("extracting.from.class");
    }

    public PsiElement[] getElements() {
        return new PsiElement[]{aClass};
    }

}
