package com.intellij.refactoring.extractclass.usageInfo;

import com.intellij.psi.PsiClass;
import com.intellij.refactoring.util.FixableUsageInfo;
import com.intellij.util.IncorrectOperationException;

public class RemoveInnerClass extends FixableUsageInfo {
    private final PsiClass innerClass;

    public RemoveInnerClass(PsiClass innerClass) {
        super(innerClass);
        this.innerClass = innerClass;
    }

    public void fixUsage() throws IncorrectOperationException{
        innerClass.delete();
    }
}
