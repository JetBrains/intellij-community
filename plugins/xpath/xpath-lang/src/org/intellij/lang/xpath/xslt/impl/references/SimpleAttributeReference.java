/*
 * Copyright 2005 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.intellij.lang.xpath.xslt.impl.references;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPolyVariantReference;
import com.intellij.psi.PsiReference;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

abstract class SimpleAttributeReference implements PsiReference {
    protected final XmlAttribute myAttribute;

    protected SimpleAttributeReference(XmlAttribute attribute) {
        myAttribute = attribute;
    }

    @Override
    @NotNull
    public String getCanonicalText() {
        return getTextRange().substring(myAttribute.getValue());
    }

    @Override
    @NotNull
    public PsiElement getElement() {
        final XmlAttributeValue value = myAttribute.getValueElement();
        assert value != null;
        return value;
    }

    @Override
    @NotNull
    public TextRange getRangeInElement() {
        return getTextRange().shiftRight(1);
    }

    @Override
    public boolean isReferenceTo(@NotNull PsiElement element) {
        if (this instanceof PsiPolyVariantReference reference) {
          final ResolveResult[] results = reference.multiResolve(false);
            for (ResolveResult result : results) {
                if (Comparing.equal(result.getElement(), element)) return true;
            }
            return false;
        } else {
            return Comparing.equal(resolve(), element);
        }
    }

    @Override
    public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
        throw new UnsupportedOperationException();
    }

    @Override
    public PsiElement handleElementRename(@NotNull String newElementName) throws IncorrectOperationException {
        myAttribute.setValue(getTextRange().replace(myAttribute.getValue(), newElementName));
        final XmlAttributeValue value = myAttribute.getValueElement();
        assert value != null;
        return value;
    }

    @Override
    @Nullable
    public final PsiElement resolve() {
        return ResolveCache.getInstance(myAttribute.getProject()).resolveWithCaching(this,
                                                                                     (ResolveCache.Resolver)(psiReference, b) -> resolveImpl(), false, false);
    }

    @Nullable
    protected abstract PsiElement resolveImpl();

    @NotNull
    protected abstract TextRange getTextRange();
}