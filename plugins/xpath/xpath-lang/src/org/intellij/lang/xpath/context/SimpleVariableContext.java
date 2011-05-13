package org.intellij.lang.xpath.context;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import org.intellij.lang.xpath.psi.XPathVariable;
import org.intellij.lang.xpath.psi.XPathVariableReference;

/*
* Created by IntelliJ IDEA.
* User: sweinreuter
* Date: 08.01.2008
*/
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