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

import org.intellij.lang.xpath.XPathElementTypes;
import org.intellij.lang.xpath.XPathFileType;
import org.intellij.lang.xpath.XPathLanguage;
import org.intellij.lang.xpath.XPathTokenTypes;
import org.intellij.lang.xpath.context.ContextProvider;
import org.intellij.lang.xpath.context.functions.Function;
import org.intellij.lang.xpath.psi.*;

import com.intellij.lang.ASTNode;
import com.intellij.lang.Language;
import com.intellij.navigation.ItemPresentation;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.util.IconLoader;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.xml.namespace.QName;
import java.util.Map;

public class XPathFunctionCallImpl extends XPathElementImpl implements XPathFunctionCall {
    private final XPathFunctionCallImpl.Reference myReference;

    public XPathFunctionCallImpl(ASTNode node) {
        super(node);

        final ASTNode nameNode = getNameNode();
        if (nameNode != null) {
            myReference = new Reference(nameNode);
        } else {
            myReference = null;
        }
    }

    @NotNull
    public XPathExpression[] getArgumentList() {
        final ASTNode[] nodes = getNode().getChildren(XPathElementTypes.EXPRESSIONS);
        final XPathExpression[] expressions = new XPathExpression[nodes.length];
        for (int i = 0; i < expressions.length; i++) {
            expressions[i] = (XPathExpression)nodes[i].getPsi();
        }
        return expressions;
    }

    public PsiElement add(@NotNull PsiElement psiElement) throws IncorrectOperationException {
        if (psiElement instanceof XPathExpression) {
            if (getNode().getChildren(XPathElementTypes.EXPRESSIONS).length > 0) {
                final XPathExpression child = XPathChangeUtil.createExpression(this, "f(a,b)");
                final ASTNode comma = child.getNode().findChildByType(XPathTokenTypes.COMMA);
                assert comma != null;
                add(comma.getPsi());
            }
        }
        final ASTNode paren = getNode().findChildByType(XPathTokenTypes.RPAREN);
        if (paren != null) {
            return super.addBefore(psiElement, paren.getPsi());
        }
        return super.add(psiElement);
    }

    @NotNull
    public String getFunctionName() {
        final ASTNode node = getNameNode();
        final String name = node != null ? node.getText() : null;
        assert name != null;
        return name;
    }

    @Nullable
    protected ASTNode getNameNode() {
        return getNode().findChildByType(XPathTokenTypes.FUNCTION_NAME);
    }

    @Nullable
    protected ASTNode getPrefixNode() {
        return getNode().findChildByType(XPathTokenTypes.EXT_PREFIX);
    }

    @NotNull
    public PrefixedName getQName() {
        final ASTNode node = getNameNode();
        assert node != null;
        return new PrefixedNameImpl(getPrefixNode(), node);
    }

    @Nullable
    public XPathFunction resolve() {
        final Reference reference = getReference();
        return reference != null ? reference.resolve() : null;
    }

    @Nullable
    public Reference getReference() {
        final ASTNode node = getNameNode();
        if (node != null) {
            return myReference;
        } else {
            return null;
        }
    }

    @NotNull
    public PsiReference[] getReferences() {
        if (getPrefixNode() != null && getNameNode() != null) {
            return new PsiReference[]{getReference(), new PrefixReferenceImpl(this, getPrefixNode())};
        }
        return super.getReferences();
    }

    @NotNull
    public XPathType getType() {
        return ContextProvider.getContextProvider(this).getFunctionType(this);
    }

    class Reference extends ReferenceBase {
        private FunctionImpl myFunction;

        public Reference(ASTNode node) {
            super(XPathFunctionCallImpl.this, node);
        }

        @Nullable
        public XPathFunction resolve() {
            if (myFunction != null) {
                return myFunction;
            } else {
                final XPathFunctionCallImpl call = XPathFunctionCallImpl.this;
                final ContextProvider contextProvider = ContextProvider.getContextProvider(call);
                final Map<QName, ? extends Function> functionDeclarations = contextProvider.getFunctionContext().getFunctions();
                final QName name = contextProvider.getQName(call);
                final Function functionDecl = functionDeclarations.get(name);

                return myFunction = new FunctionImpl(functionDecl);
            }
        }

        @NotNull
        public Object[] getVariants() {
            return EMPTY_ARRAY;
        }

        class FunctionImpl extends LightElement implements XPathFunction, ItemPresentation {
            private final Function myFunctionDecl;

            public FunctionImpl(Function functionDecl) {
                super(getElement().getManager(), Language.findInstance(XPathLanguage.class));
                myFunctionDecl = functionDecl;
            }

            @Override
            public PsiElement getContext() {
                return XPathFunctionCallImpl.this;
            }

            public String getName() {
                return getFunctionName();
            }

            public String toString() {
                return "Function: " + getName();
            }

            @SuppressWarnings({"ConstantConditions"})
            public String getText() {
                return getName();
            }

            public ItemPresentation getPresentation() {
                return this;
            }

            @Nullable
            public Icon getIcon(boolean open) {
                return getIcon(0);
            }

            @Nullable
            public String getLocationString() {
                return null;
            }

            @Nullable
            public String getPresentableText() {
                return myFunctionDecl != null ? myFunctionDecl.buildSignature(getName()) +
                        ": " + getType().getName() : null;
            }

            @Nullable
            public TextAttributesKey getTextAttributesKey() {
                return null;
            }

            public Icon getIcon(int i) {
                return IconLoader.getIcon("/icons/function.png");
            }

            public void accept(@NotNull PsiElementVisitor visitor) {
            }

            public PsiElement copy() {
                return this;
            }

            @NotNull
            public Language getLanguage() {
                return XPathFileType.XPATH.getLanguage();
            }

            public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
                throw new IncorrectOperationException();
            }

            public boolean isValid() {
                return true;
            }

            public int hashCode() {
                final String name = getName();
                return name != null ? name.hashCode() : 0;
            }

            public boolean equals(Object obj) {
                if (obj == null || obj.getClass() != getClass()) return false;
                final String name = ((FunctionImpl)obj).getName();
                return name != null && name.equals(getName()) || getName() == null;
            }

            public Function getDeclaration() {
                return myFunctionDecl;
            }

            @Override
            public boolean isWritable() {
                return false;
            }

            @Override
            public boolean isPhysical() {
                // hack
                // required to prevent renaming of functions. Shouldn't IDEA check for isWritable()?
                // com.intellij.refactoring.rename.PsiElementRenameHandler:
                // if (!PsiManager.getInstance(project).isInProject(element) && element.isPhysical()) { ... }
                return true;
            }
        }
    }
}
