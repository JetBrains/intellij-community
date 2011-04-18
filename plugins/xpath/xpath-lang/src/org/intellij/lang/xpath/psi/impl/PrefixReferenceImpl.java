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
import com.intellij.util.IncorrectOperationException;
import org.intellij.lang.xpath.context.ContextProvider;
import org.intellij.lang.xpath.context.NamespaceContext;
import org.intellij.lang.xpath.psi.PrefixReference;
import org.intellij.lang.xpath.psi.QNameElement;
import org.intellij.lang.xpath.psi.XPathNodeTest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class PrefixReferenceImpl extends ReferenceBase implements PrefixReference {

  public PrefixReferenceImpl(QNameElement element, ASTNode nameNode) {
    super(element, nameNode);
  }

  @Nullable
  public PsiElement resolve() {
    final ContextProvider provider = getElement().getXPathContext();
    final NamespaceContext namespaceContext = provider.getNamespaceContext();
    if (namespaceContext != null) {
      return namespaceContext.resolve(getCanonicalText(), provider.getContextElement());
    } else {
      return null;
    }
  }

  @SuppressWarnings({"ConstantConditions"})
  @Override
  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    final XPathNodeTest expr =
            (XPathNodeTest)XPathChangeUtil.createExpression(getElement(), newElementName + ":x").getFirstChild().getChildren()[1];
    final ASTNode nameNode = getNameNode();
    nameNode.getTreeParent().replaceChild(nameNode, ((PrefixedNameImpl)expr.getQName()).getPrefixNode());
    return getElement();
  }

  @NotNull
  public Object[] getVariants() {
    // handled in XPathCompletionData
    return EMPTY_ARRAY;
  }

  public boolean isSoft() {
    if ("*".equals(getPrefix())) {
      return true;
    }
    final ContextProvider context = getElement().getXPathContext();
    if (context instanceof ContextProvider.DefaultProvider) return true;

    return !canResolve();
  }

  public String getPrefix() {
    return getCanonicalText();
  }

  public boolean isUnresolved() {
    if (!canResolve()) return true;

    if (resolve() != null) {
      return false;
    }

    final ContextProvider provider = getElement().getXPathContext();
    final NamespaceContext namespaceContext = provider.getNamespaceContext();
    assert namespaceContext != null;

    final String prefix = getCanonicalText();
    return !namespaceContext.getKnownPrefixes(provider.getContextElement()).contains(prefix);
  }

  private boolean canResolve() {
    final NamespaceContext namespaceContext = getElement().getXPathContext().getNamespaceContext();
    return namespaceContext != null && !"xml".equals(getCanonicalText());
  }
}
