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
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.util.IncorrectOperationException;
import org.intellij.lang.xpath.psi.XPathElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class ReferenceBase implements PsiReference {
    private final XPathElement element;
    private final ASTNode nameNode;

    public ReferenceBase(XPathElement element, ASTNode nameNode) {
        this.element = element;
        this.nameNode = nameNode;
    }

    @NotNull
    public XPathElement getElement() {
        return element;
    }

    @NotNull
    public TextRange getRangeInElement() {
        final int outer = element.getTextRange().getStartOffset();
        return TextRange.from(nameNode.getTextRange().getStartOffset() - outer, nameNode.getTextLength());
    }

    @Nullable
    public PsiElement resolve() {
        return null;
    }

    @NotNull
    public String getCanonicalText() {
        return nameNode.getText();
    }

    public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
        throw new IncorrectOperationException("unsupported");
    }

    public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
        throw new IncorrectOperationException("unsupported");
    }

    public boolean isReferenceTo(PsiElement element) {
        return Comparing.equal(resolve(), element);
    }

    @NotNull
    public abstract Object[] getVariants();

    public boolean isSoft() {
        return true;
    }

    public ASTNode getNameNode() {
      return nameNode;
    }
}
