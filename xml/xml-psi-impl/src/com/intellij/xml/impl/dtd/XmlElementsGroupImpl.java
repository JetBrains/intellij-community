// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xml.impl.dtd;

import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.psi.xml.XmlContentParticle;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlElementsGroup;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public final class XmlElementsGroupImpl implements XmlElementsGroup {
  private final XmlContentParticle myParticle;
  private final XmlElementsGroup myParent;
  private final NotNullLazyValue<List<XmlElementsGroup>> mySubGroups;

  public XmlElementsGroupImpl(@NotNull XmlContentParticle particle, XmlElementsGroup parent) {
    myParticle = particle;
    myParent = parent;
    mySubGroups = NotNullLazyValue.lazy(() -> {
      return ContainerUtil.map(myParticle.getSubParticles(), xmlContentParticle -> new XmlElementsGroupImpl(xmlContentParticle, this));
    });
  }

  @Override
  public int getMinOccurs() {
    switch (myParticle.getQuantifier()) {
      case ONE_OR_MORE:
      case REQUIRED:
        return 1;
      case ZERO_OR_MORE:
      case OPTIONAL:
        return 0;
    }
    throw new AssertionError(myParticle.getQuantifier());
  }

  @Override
  public int getMaxOccurs() {
    switch (myParticle.getQuantifier()) {
      case ONE_OR_MORE:
      case ZERO_OR_MORE:
        return Integer.MAX_VALUE;
      case OPTIONAL:
      case REQUIRED:
        return 1;
    }
    throw new AssertionError(myParticle.getQuantifier());
  }

  @Override
  public Type getGroupType() {
    switch (myParticle.getType()) {
      case SEQUENCE:
        return Type.SEQUENCE;
      case CHOICE:
        return Type.CHOICE;
      case ELEMENT:
        return Type.LEAF;
    }
    throw new AssertionError(myParticle.getType());
  }

  @Override
  public XmlElementsGroup getParentGroup() {
    return myParent;
  }

  @Override
  public List<XmlElementsGroup> getSubGroups() {
    return mySubGroups.getValue();
  }

  @Override
  public XmlElementDescriptor getLeafDescriptor() {
    return myParticle.getElementDescriptor();
  }
}
