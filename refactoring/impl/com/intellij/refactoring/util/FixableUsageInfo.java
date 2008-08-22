package com.intellij.refactoring.util;

import com.intellij.psi.PsiElement;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;



@SuppressWarnings({"AbstractClassExtendsConcreteClass"})
public abstract class FixableUsageInfo extends UsageInfo {
    protected FixableUsageInfo(PsiElement element) {
        super(element);
    }

  public abstract void fixUsage() throws IncorrectOperationException;

}
