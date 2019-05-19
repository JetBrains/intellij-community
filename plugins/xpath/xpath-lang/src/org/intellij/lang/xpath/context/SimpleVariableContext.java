// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.lang.xpath.context;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.psi.PsiElement;
import org.intellij.lang.xpath.psi.XPathVariable;
import org.intellij.lang.xpath.psi.XPathVariableReference;
import org.jetbrains.annotations.NotNull;

public abstract class SimpleVariableContext implements VariableContext<String> {

    @Override
    public XPathVariable resolve(XPathVariableReference reference) {
        return null;
    }

    @Override
    public boolean canResolve() {
        return false;
    }

    @Override
    @NotNull
    public IntentionAction[] getUnresolvedVariableFixes(XPathVariableReference reference) {
        return IntentionAction.EMPTY_ARRAY;
    }

    @Override
    public boolean isReferenceTo(PsiElement element, XPathVariableReference reference) {
        return false;
    }
}