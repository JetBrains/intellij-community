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
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.intellij.lang.xpath.XPathFileType;
import org.intellij.lang.xpath.XPathTokenTypes;
import org.intellij.lang.xpath.context.VariableContext;
import org.intellij.lang.xpath.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class XPathVariableReferenceImpl extends XPathElementImpl implements XPathVariableReference {
    private static final TokenSet QNAME_FILTER = TokenSet.create(XPathTokenTypes.VARIABLE_PREFIX, XPathTokenTypes.VARIABLE_NAME);

    public XPathVariableReferenceImpl(ASTNode node) {
        super(node);
    }

    @Override
    @NotNull
    public String getReferencedName() {
        return getText().substring(1);
    }

    @Override
    @NotNull
    public XPathType getType() {
        final XPathVariable xPathVariable = resolve();
        if (xPathVariable != null) {
            return xPathVariable.getType();
        }
        return XPathType.UNKNOWN;
    }

    @Override
    public PsiReference getReference() {
        return this;
    }

    @Override
    @NotNull
    public PsiElement getElement() {
        return this;
    }

    @Override
    public int getTextOffset() {
        return getTextRange().getStartOffset() + 1;
    }

    @Override
    @NotNull
    public TextRange getRangeInElement() {
        return TextRange.from(1, getTextLength() - 1);
    }

    @Override
    @Nullable
    public XPathVariable resolve() {
      if (getContainingFile().getLanguage() == XPathFileType.XPATH2.getLanguage()) {
        XPathVariableHolder f = PsiTreeUtil.getParentOfType(this, XPathVariableHolder.class, true);
        while (f != null) {
          final XPathVariable variable = findVariable(f.getVariables(), getReferencedName());
          if (variable != null) {
            return variable;
          }
          f = PsiTreeUtil.getParentOfType(f, XPathVariableHolder.class, true);
        }
      }
      final VariableContext context = getXPathContext().getVariableContext();
      if (context == null) {
        return null;
      }
      return context.resolve(this);
    }

  @Nullable
  private static XPathVariable findVariable(XPathVariableDeclaration[] declarations, String referencedName) {
    for (XPathVariableDeclaration decl : declarations) {
      final XPathVariable v = decl.getVariable();
      if (v != null) {
        if (referencedName.equals(v.getName())) {
          return v;
        }
      }
    }
    return null;
  }

  @Override
  @NotNull
    public String getCanonicalText() {
        return getText();
    }

    @Override
    public PsiElement handleElementRename(@NotNull String newElementName) throws IncorrectOperationException {
        renameTo(newElementName);
        return this;
    }

    @Override
    public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
        renameTo(((PsiNamedElement)element).getName());
        return this;
    }

    private void renameTo(String newElementName) {
        final XPathVariableReference child = XPathChangeUtil.createVariableReference(this, newElementName);

        final PrefixedNameImpl newName = ((PrefixedNameImpl)child.getQName());
        final PrefixedNameImpl oldName = ((PrefixedNameImpl)getQName());
        assert newName != null;
        assert oldName != null;

        final ASTNode localNode = newName.getLocalNode();
        getNode().replaceChild(oldName.getLocalNode(), localNode);
    }

    @Override
    public boolean isReferenceTo(@NotNull PsiElement element) {
        if (element instanceof XPathVariable) {
            final XPathVariable resolved = resolve();
            if (getReferencedName().equals(((XPathVariable)element).getName())) {
                if (element.equals(resolved)) {
                    return true;
                }
            }
        }
        final VariableContext context = getXPathContext().getVariableContext();
        if (context != null) {
            return context.isReferenceTo(element, this);
        }
        return false;
    }

    @Override
    public boolean isSoft() {
        return true;
    }

    @Override
    @Nullable
    public PrefixedName getQName() {
        final ASTNode[] nodes = getNode().getChildren(QNAME_FILTER);
        if (nodes.length == 1) {
            return new PrefixedNameImpl(nodes[0]);
        } else if (nodes.length == 2) {
            return new PrefixedNameImpl(nodes[0], nodes[1]);
        }
        return null;
    }

    public int hashCode() {
        return getReferencedName().hashCode();
    }

  @Override
  public void accept(XPathElementVisitor visitor) {
    visitor.visitXPathVariableReference(this);
  }
}
