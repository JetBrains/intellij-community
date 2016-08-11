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
package com.intellij.psi.impl.source.xml;

import com.intellij.psi.xml.XmlTag;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlElementsGroup;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Dmitry Avdeev
 */
class XmlContentDFAImpl extends XmlContentDFA {

  enum Result {
    NONE,
    CONSUME,
    PROCEED_TO_NEXT
  }

  private final XmlElementsGroup myGroup;
  private int myOccurs;
  private XmlContentDFAImpl myLastChild;

  @Nullable
  public static XmlContentDFA createContentDFA(XmlTag parentTag) {
    XmlElementDescriptor descriptor = parentTag.getDescriptor();
    if (descriptor == null) {
      return null;
    }
    XmlElementsGroup topGroup = descriptor.getTopGroup();
    if (topGroup == null) {
      return null;
    }
    return new XmlContentDFAImpl(topGroup);
  }

  private XmlContentDFAImpl(@NotNull XmlElementsGroup group) {
    myGroup = group;
  }

  @Override
  public List<XmlElementDescriptor> getPossibleElements() {
    ArrayList<XmlElementDescriptor> list = new ArrayList<>();
    getPossibleElements(list);
    return list;
  }

  private void getPossibleElements(List<XmlElementDescriptor> elements) {
    switch (myGroup.getGroupType()) {
      case SEQUENCE:
        getLastChild();
        while (myLastChild != null) {
          myLastChild.getPossibleElements(elements);
          if (myLastChild.myGroup.getMinOccurs() == 0) {
            myLastChild = getNextSubGroup();
          }
          else return;
        }
        break;
      case CHOICE:
      case ALL:
      case GROUP:
        for (XmlElementsGroup group : myGroup.getSubGroups()) {
          new XmlContentDFAImpl(group).getPossibleElements(elements);
        }
        break;
      case LEAF:
        ContainerUtil.addIfNotNull(elements, myGroup.getLeafDescriptor());
        break;
    }
  }

  @Override
  public void transition(XmlTag xmlTag) {
    XmlElementDescriptor descriptor = xmlTag.getDescriptor();
    if (descriptor != null) {
      doTransition(descriptor);
    }
  }

  private Result doTransition(@NotNull XmlElementDescriptor element) {
    if (myGroup.getGroupType() == XmlElementsGroup.Type.LEAF) {
      if (element.equals(myGroup.getLeafDescriptor())) {
        return consume();
      }
      else return Result.NONE;
    }
    return processSubGroups(element);
  }

  private Result consume() {
    return ++myOccurs >= myGroup.getMaxOccurs() ? Result.PROCEED_TO_NEXT : Result.CONSUME;
  }

  private Result processSubGroups(XmlElementDescriptor element) {
    getLastChild();
    while (myLastChild != null) {
      Result result = myLastChild.doTransition(element);
      switch (result) {
        case CONSUME:
          return Result.CONSUME;
        case NONE:
          myLastChild = getNextSubGroup();
          break;
        case PROCEED_TO_NEXT:
          myLastChild = getNextSubGroup();
          return myLastChild == null ? Result.PROCEED_TO_NEXT : Result.CONSUME;
      }
    }
    return Result.NONE;
  }

  private void getLastChild() {
    if (myLastChild == null) {
      List<XmlElementsGroup> subGroups = myGroup.getSubGroups();
      if (!subGroups.isEmpty()) {
        myLastChild = new XmlContentDFAImpl(subGroups.get(0));
      }
    }
  }

  @Nullable
  private XmlContentDFAImpl getNextSubGroup() {
    List<XmlElementsGroup> subGroups = myGroup.getSubGroups();
    int i = subGroups.indexOf(myLastChild.myGroup) + 1;
    return i == subGroups.size() ? null : new XmlContentDFAImpl(subGroups.get(i));
  }
}
