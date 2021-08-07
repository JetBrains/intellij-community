// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.wrapreturnvalue;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.JavaRareRefactoringsBundle;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import org.jetbrains.annotations.NotNull;

class WrapReturnValueUsageViewDescriptor implements UsageViewDescriptor {

    @NotNull
    private final PsiMethod method;

    WrapReturnValueUsageViewDescriptor(@NotNull PsiMethod method,
                                       UsageInfo[] usages){
        super();
        this.method = method;
    }

    @Override
    public PsiElement @NotNull [] getElements(){
        return new PsiElement[]{method};
    }

    @Override
    public String getProcessedElementsHeader(){
        return JavaRareRefactoringsBundle.message("method.whose.return.are.to.wrapped");
    }

    @NotNull
    @Override
    public String getCodeReferencesText(int usagesCount, int filesCount){
        return JavaRareRefactoringsBundle.message("references.to.be.modified.usage.view", usagesCount, filesCount);
    }
}
