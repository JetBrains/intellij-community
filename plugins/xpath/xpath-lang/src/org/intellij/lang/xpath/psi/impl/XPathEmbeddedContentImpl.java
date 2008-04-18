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
package org.intellij.lang.xpath.psi.impl;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTagChild;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings({"ConstantConditions"})
public class XPathEmbeddedContentImpl extends XPathElementImpl implements XmlTagChild {
    public XPathEmbeddedContentImpl(ASTNode node) {
        super(node);
    }

    public XmlTag getParentTag() {
        final PsiElement parent = getParent();
        if(parent instanceof XmlTag) return (XmlTag)parent;
        return null;
    }

    public XmlTagChild getNextSiblingInTag() {
        PsiElement nextSibling = getNextSibling();
        if(nextSibling instanceof XmlTagChild) return (XmlTagChild)nextSibling;
        return null;
    }

    public XmlTagChild getPrevSiblingInTag() {
        final PsiElement prevSibling = getPrevSibling();
        if(prevSibling instanceof XmlTagChild) return (XmlTagChild)prevSibling;
        return null;
    }

    @SuppressWarnings({"RawUseOfParameterizedType"})
    public boolean processElements(PsiElementProcessor processor, PsiElement place) {
        // TODO
        return true;
    }

    @Override
    public boolean processDeclarations(@NotNull PsiScopeProcessor processor, @NotNull ResolveState substitutor, PsiElement lastParent, @NotNull PsiElement place) {
        if (lastParent == null) {
            PsiElement child = getFirstChild();
            while (child != null) {
                if (!child.processDeclarations(processor, substitutor, null, place)) return false;
                child = child.getNextSibling();
            }
        }

        return true;
    }
}