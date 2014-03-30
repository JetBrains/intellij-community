/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.xml.impl.dtd;

import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.psi.xml.XmlContentParticle;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlElementsGroup;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public class XmlElementsGroupImpl implements XmlElementsGroup {

  private final XmlContentParticle myParticle;
  private final XmlElementsGroup myParent;
  private final NotNullLazyValue<List<XmlElementsGroup>> mySubGroups = new NotNullLazyValue<List<XmlElementsGroup>>() {
    @NotNull
    @Override
    protected List<XmlElementsGroup> compute() {
      return ContainerUtil.map(myParticle.getSubParticles(), new Function<XmlContentParticle, XmlElementsGroup>() {
        @Override
        public XmlElementsGroup fun(XmlContentParticle xmlContentParticle) {
          return new  XmlElementsGroupImpl(xmlContentParticle, XmlElementsGroupImpl.this);
        }
      });
    }
  };

  public XmlElementsGroupImpl(@NotNull XmlContentParticle particle, XmlElementsGroup parent) {
    myParticle = particle;
    myParent = parent;
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
