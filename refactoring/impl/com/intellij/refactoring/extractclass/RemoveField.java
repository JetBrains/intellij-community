package com.intellij.refactoring.extractclass;

import com.intellij.psi.PsiField;
import com.intellij.refactoring.base.RefactorJUsageInfo;
import com.intellij.util.IncorrectOperationException;

class RemoveField extends RefactorJUsageInfo {
    private final PsiField field;

    RemoveField(PsiField field) {
        super(field);
        this.field = field;
    }

    public void fixUsage() throws IncorrectOperationException {
        field.delete();
    }
}
