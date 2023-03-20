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
    return switch (myParticle.getQuantifier()) {
      case ONE_OR_MORE, REQUIRED -> 1;
      case ZERO_OR_MORE, OPTIONAL -> 0;
    };
  }

  @Override
  public int getMaxOccurs() {
    return switch (myParticle.getQuantifier()) {
      case ONE_OR_MORE, ZERO_OR_MORE -> Integer.MAX_VALUE;
      case OPTIONAL, REQUIRED -> 1;
    };
  }

  @Override
  public Type getGroupType() {
    return switch (myParticle.getType()) {
      case SEQUENCE -> Type.SEQUENCE;
      case CHOICE -> Type.CHOICE;
      case ELEMENT -> Type.LEAF;
    };
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
