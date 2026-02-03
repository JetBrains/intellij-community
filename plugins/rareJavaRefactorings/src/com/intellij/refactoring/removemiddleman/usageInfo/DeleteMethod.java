// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.removemiddleman.usageInfo;

import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.util.FixableUsageInfo;
import com.intellij.util.IncorrectOperationException;

public class DeleteMethod extends FixableUsageInfo {
    private final PsiMethod method;

    public DeleteMethod(PsiMethod method) {
        super(method);
        this.method = method;
    }

    @Override
    public void fixUsage() throws IncorrectOperationException {
        method.delete();
    }
}
