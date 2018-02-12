package org.intellij.lang.xpath.context;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import org.intellij.lang.xpath.psi.XPathVariable;
import org.intellij.lang.xpath.psi.XPathVariableReference;

public abstract class SimpleVariableContext implements VariableContext<String> {

    public XPathVariable resolve(XPathVariableReference reference) {
        return null;
    }

    public boolean canResolve() {
        return false;
    }

    @NotNull
    public IntentionAction[] getUnresolvedVariableFixes(XPathVariableReference reference) {
        return IntentionAction.EMPTY_ARRAY;
    }

    public boolean isReferenceTo(PsiElement element, XPathVariableReference reference) {
        return false;
    }
}