package com.intellij.refactoring.extractclass;

import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.refactoring.base.RefactorJUsageInfo;
import com.intellij.refactoring.psi.MutationUtils;
import com.intellij.util.IncorrectOperationException;

class ReplaceClassReference extends RefactorJUsageInfo {
    private final PsiJavaCodeReferenceElement reference;
    private final String newClassName;

    ReplaceClassReference(PsiJavaCodeReferenceElement reference, String newClassName) {
        super(reference);
        this.reference = reference;
        this.newClassName = newClassName;
    }

    public void fixUsage() throws IncorrectOperationException {
        MutationUtils.replaceReference(newClassName, reference);
    }
}
