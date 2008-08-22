package com.intellij.refactoring.extractclass;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactorJBundle;
import com.intellij.refactoring.psi.MyUsageViewUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;

class ExtractClassUsageViewDescriptor implements UsageViewDescriptor {
    private PsiClass aClass;

    ExtractClassUsageViewDescriptor(PsiClass aClass, UsageInfo[] usages) {
        super();
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

  public String getCommentReferencesText(int usagesCount, int filesCount) {
        return null;
    }
}
