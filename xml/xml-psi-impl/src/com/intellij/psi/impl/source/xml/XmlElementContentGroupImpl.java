/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.psi.impl.source.xml;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.xml.XmlContentParticle;
import com.intellij.psi.xml.XmlElementContentGroup;
import com.intellij.psi.xml.XmlElementType;
import com.intellij.psi.xml.XmlToken;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xml.XmlElementDescriptor;
import org.jetbrains.annotations.NotNull;

/**
 * @author Dmitry Avdeev
 */
public class XmlElementContentGroupImpl  extends XmlElementImpl implements XmlElementContentGroup,
                                                                           XmlElementType {

  private final NotNullLazyValue<XmlContentParticle[]> myParticles = new NotNullLazyValue<XmlContentParticle[]>() {
    @NotNull
    @Override
    protected XmlContentParticle[] compute() {
      return ContainerUtil.map(getChildren(TokenSet.create(XML_ELEMENT_CONTENT_GROUP, XML_NAME)), new Function<ASTNode, XmlContentParticle>() {
        @Override
        public XmlContentParticle fun(ASTNode astNode) {
          PsiElement element = astNode.getPsi();
          assert element != null;
          return element instanceof XmlToken ? new XmlContentParticleImpl((XmlToken)element) : (XmlContentParticle)element;
        }
      }, new XmlContentParticle[0]);
    }
  };

  public XmlElementContentGroupImpl() {
    super(XML_ELEMENT_CONTENT_GROUP);
  }

  @Override
  public Type getType() {
    return findElementByTokenType(XML_BAR) == null ? Type.SEQUENCE : Type.CHOICE;
  }

  @Override
  public Quantifier getQuantifier() {
    return XmlContentParticleImpl.getQuantifierImpl(this);
  }

  @Override
  public XmlContentParticle[] getSubParticles() {
    return myParticles.getValue();
  }

  @Override
  public XmlElementDescriptor getElementDescriptor() {
    return null;
  }
}
