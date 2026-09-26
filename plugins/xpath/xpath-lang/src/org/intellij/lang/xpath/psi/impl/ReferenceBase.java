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

    @Override
    public @NotNull XPathElement getElement() {
        return element;
    }

    @Override
    public @NotNull TextRange getRangeInElement() {
        final int outer = element.getTextRange().getStartOffset();
        return TextRange.from(nameNode.getTextRange().getStartOffset() - outer, nameNode.getTextLength());
    }

    @Override
    public @Nullable PsiElement resolve() {
        return null;
    }

    @Override
    public @NotNull String getCanonicalText() {
        return nameNode.getText();
    }

    @Override
    public PsiElement handleElementRename(@NotNull String newElementName) throws IncorrectOperationException {
        throw new IncorrectOperationException("unsupported");
    }

    @Override
    public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
        throw new IncorrectOperationException("unsupported");
    }

    @Override
    public boolean isReferenceTo(@NotNull PsiElement element) {
        return Comparing.equal(resolve(), element);
    }

    @Override
    public boolean isSoft() {
        return true;
    }

    public ASTNode getNameNode() {
      return nameNode;
    }
}
