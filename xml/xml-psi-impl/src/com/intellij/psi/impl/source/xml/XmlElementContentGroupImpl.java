// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.xml;

import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.xml.XmlContentParticle;
import com.intellij.psi.xml.XmlElementContentGroup;
import com.intellij.psi.xml.XmlElementType;
import com.intellij.psi.xml.XmlToken;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xml.XmlElementDescriptor;

/**
 * @author Dmitry Avdeev
 */
public final class XmlElementContentGroupImpl  extends XmlElementImpl implements XmlElementContentGroup,
                                                                           XmlElementType {
  private final NotNullLazyValue<XmlContentParticle[]> myParticles = NotNullLazyValue.lazy(() -> {
    return ContainerUtil.map(getChildren(TokenSet.create(XML_ELEMENT_CONTENT_GROUP, XML_NAME)), astNode -> {
      PsiElement element = astNode.getPsi();
      assert element != null;
      return element instanceof XmlToken ? new XmlContentParticleImpl((XmlToken)element) : (XmlContentParticle)element;
    }, new XmlContentParticle[0]);
  });

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
