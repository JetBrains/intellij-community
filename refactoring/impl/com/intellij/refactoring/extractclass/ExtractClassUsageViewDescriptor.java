package com.intellij.refactoring.extractclass;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactorJBundle;
import com.intellij.refactoring.psi.MyUsageViewUtil;
import com.intellij.usageView.UsageViewDescriptor;
import org.jetbrains.annotations.NotNull;

class ExtractClassUsageViewDescriptor implements UsageViewDescriptor {
    private PsiClass aClass;

    ExtractClassUsageViewDescriptor(PsiClass aClass) {
        super();
        this.aClass = aClass;
    }


    public String getCodeReferencesText(int usagesCount, int filesCount) {
        return RefactorJBundle.message("references.to.extract") + MyUsageViewUtil.getUsageCountInfo(usagesCount, filesCount, "reference");
    }

    public String getProcessedElementsHeader() {
        return RefactorJBundle.message("extracting.from.class");
    }

    @NotNull
    public PsiElement[] getElements() {
        return new PsiElement[]{aClass};
    }

  public String getCommentReferencesText(int usagesCount, int filesCount) {
        return null;
    }
}
