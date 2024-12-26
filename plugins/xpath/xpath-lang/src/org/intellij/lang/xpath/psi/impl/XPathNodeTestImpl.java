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
import com.intellij.psi.PsiReference;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.util.PsiTreeUtil;
import org.intellij.lang.xpath.XPathTokenTypes;
import org.intellij.lang.xpath.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class XPathNodeTestImpl extends XPathElementImpl implements XPathNodeTest {
    public XPathNodeTestImpl(ASTNode node) {
        super(node);
    }

    @Override
    public @NotNull XPathStep getStep() {
        final XPathStep step = PsiTreeUtil.getParentOfType(this, XPathStep.class);
        assert step != null : unexpectedPsiAssertion();
        return step;
    }

    @Override
    public boolean isNameTest() {
        return getNode().findChildByType(XPathTokenTypes.NCNAME) != null || getNode().findChildByType(XPathTokenTypes.STAR) != null;
    }

    @Override
    public @Nullable PrefixedName getQName() {
        final ASTNode[] nodes = getNode().getChildren(TokenSet.create(XPathTokenTypes.NCNAME));
        if (nodes.length == 0) {
            final ASTNode node = getNode().findChildByType(XPathTokenTypes.STAR);
            if (node != null) {
                return new PrefixedNameImpl(null, node);
            }
        } else if (nodes.length == 1) {
            final ASTNode star = getNode().findChildByType(XPathTokenTypes.STAR);
            if (star != null) {
                return star.getTextRange().getStartOffset() > nodes[0].getTextRange().getStartOffset() ?
                       new PrefixedNameImpl(nodes[0], star) : new PrefixedNameImpl(star, nodes[0]);
            } else {
                return new PrefixedNameImpl(nodes[0]);
            }
        } else if (nodes.length == 2) {
            return new PrefixedNameImpl(nodes[0], nodes[1]);
        }
        return null;
    }

    @Override
    public int getTextOffset() {
        final PrefixedNameImpl qName = ((PrefixedNameImpl)getQName());
        if (qName != null) {
            return qName.getLocalNode().getStartOffset();
        } else {
            return super.getTextOffset();
        }
    }

    @Override
    public @NotNull PrincipalType getPrincipalType() {
        final XPathStep step = getStep();

        final XPathAxisSpecifier axisSpecifier = step.getAxisSpecifier();
        if (axisSpecifier == null) return PrincipalType.UNKNOWN;

        final Axis axis = axisSpecifier.getAxis();
        if (axis == Axis.ATTRIBUTE) {
            return PrincipalType.ATTRIBUTE;
        } else if (axis == Axis.NAMESPACE) {
            return PrincipalType.NAMESPACE;
        } else {
            return PrincipalType.ELEMENT;
        }
    }

    @Override
    public @Nullable PsiReference getReference() {
        final ASTNode name = getNode().findChildByType(XPathTokenTypes.NCNAME);
        if (name != null) {
            return new Reference(this, name);
        }
        return null;
    }

    @Override
    public PsiReference @NotNull [] getReferences() {
        final PrefixedName prefixedName = getQName();
        if (prefixedName != null && prefixedName.getPrefix() != null && getReference() != null) {
            return new PsiReference[]{ getReference(), new PrefixReferenceImpl(this, ((PrefixedNameImpl)prefixedName).getPrefixNode() )};
        }
        return super.getReferences();
    }

    static class Reference extends ReferenceBase {
        Reference(XPathNodeTest element, ASTNode nameNode) {
            super(element, nameNode);
        }

        @Override
        public Object @NotNull [] getVariants() {
            // handled in XPathCompletionData
            return EMPTY_ARRAY;
        }
    }

  @Override
  public void accept(XPathElementVisitor visitor) {
    visitor.visitXPathNodeTest(this);
  }
}
