package com.intellij.refactoring.base;

import com.intellij.psi.PsiElement;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;



@SuppressWarnings({"AbstractClassExtendsConcreteClass"})
public abstract class RefactorJUsageInfo extends UsageInfo {
    protected RefactorJUsageInfo(PsiElement element) {
        super(element);
    }

    public abstract void fixUsage() throws IncorrectOperationException;

}
