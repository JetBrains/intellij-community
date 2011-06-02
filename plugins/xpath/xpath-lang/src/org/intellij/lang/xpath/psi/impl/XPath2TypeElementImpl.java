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
import com.intellij.psi.PsiReferenceService;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.ArrayUtil;
import org.intellij.lang.xpath.XPathTokenTypes;
import org.intellij.lang.xpath.psi.*;
import org.jetbrains.annotations.NotNull;

import javax.xml.namespace.QName;

public class XPath2TypeElementImpl extends XPath2ElementImpl implements XPath2TypeElement {
  public XPath2TypeElementImpl(ASTNode node) {
    super(node);
  }

  @Override
  public XPathType getDeclaredType() {
    final QName qName = getXPathContext().getQName(this);
    final XPath2Type type;
    if (qName != null) {
      type = XPath2Type.fromName(qName);
    } else {
      final PrefixedName qn = getQName();
      if (qn != null) {
        final String prefix = qn.getPrefix();
        if (prefix != null && prefix.equals("xs")) {
          type = XPath2Type.fromName(new QName(XPath2Type.XMLSCHEMA_NS, qn.getLocalName()));
        } else {
          type = null;
        }
      } else {
        type = null;
      }
    }
    return type != null ? type : XPathType.UNKNOWN;
  }

  @Override
  public PrefixedName getQName() {
    final ASTNode[] nodes = getNode().getChildren(TokenSet.create(XPathTokenTypes.NCNAME));
    if (nodes.length == 0) {
      final ASTNode node = getNode().findChildByType(XPathTokenTypes.STAR);
      if (node != null) {
        return new PrefixedNameImpl(null, node);
      }
    } else if (nodes.length == 1) {
      return new PrefixedNameImpl(nodes[0]);
    } else if (nodes.length == 2) {
      return new PrefixedNameImpl(nodes[0], nodes[1]);
    }
    return null;
  }

  @NotNull
  public PsiReference[] getReferences() {
    final PrefixedName prefixedName = getQName();
    if (prefixedName != null && prefixedName.getPrefix() != null) {
      final PsiReference[] references =
        ReferenceProvidersRegistry.getReferencesFromProviders(this, PsiReferenceService.Hints.NO_HINTS);

      final PrefixReferenceImpl reference = new PrefixReferenceImpl(this, ((PrefixedNameImpl)prefixedName).getPrefixNode());
      return references.length > 0 ? ArrayUtil.append(references, reference) : new PsiReference[]{ reference };
    }
    return super.getReferences();
  }

  public void accept(XPath2ElementVisitor visitor) {
    visitor.visitXPath2TypeElement(this);
  }
}
