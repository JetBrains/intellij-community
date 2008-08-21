package com.intellij.refactoring.wrapreturnvalue;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.RefactorJBundle;
import com.intellij.refactoring.base.BaseUsageViewDescriptor;
import com.intellij.refactoring.psi.MyUsageViewUtil;
import com.intellij.usageView.UsageInfo;
import org.jetbrains.annotations.NotNull;

class WrapReturnValueUsageViewDescriptor extends BaseUsageViewDescriptor{

    @NotNull
    private PsiMethod method;

    WrapReturnValueUsageViewDescriptor(@NotNull PsiMethod method,
                                       UsageInfo[] usages){
        super(usages);
        this.method = method;
    }

    public PsiElement[] getElements(){
        return new PsiElement[]{method};
    }

    public String getProcessedElementsHeader(){
        return RefactorJBundle.message("method.whose.return.are.to.wrapped");
    }

    public String getCodeReferencesText(int usagesCount, int filesCount){
        return RefactorJBundle.message("references.to.be.modified.usage.view",
                MyUsageViewUtil.getUsageCountInfo(usagesCount, filesCount, RefactorJBundle.message("reference")));
    }
}
