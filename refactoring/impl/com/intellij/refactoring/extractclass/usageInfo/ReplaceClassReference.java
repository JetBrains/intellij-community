package com.intellij.refactoring.extractclass.usageInfo;

import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.refactoring.psi.MutationUtils;
import com.intellij.refactoring.util.FixableUsageInfo;
import com.intellij.util.IncorrectOperationException;

public class ReplaceClassReference extends FixableUsageInfo {
    private final PsiJavaCodeReferenceElement reference;
    private final String newClassName;

    public ReplaceClassReference(PsiJavaCodeReferenceElement reference, String newClassName) {
        super(reference);
        this.reference = reference;
        this.newClassName = newClassName;
    }

    public void fixUsage() throws IncorrectOperationException {
        MutationUtils.replaceReference(newClassName, reference);
    }
}
