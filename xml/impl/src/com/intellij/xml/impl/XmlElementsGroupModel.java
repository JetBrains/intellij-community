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
package com.intellij.xml.impl;

import com.intellij.openapi.util.Ref;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.Processor;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlElementsGroup;
import com.intellij.xml.impl.schema.XmlElementDescriptorImpl;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Dmitry Avdeev
 */
public class XmlElementsGroupModel {

  private final XmlElementsGroup myTopGroup;

  public XmlElementsGroupModel(XmlElementsGroup topGroup) {
    myTopGroup = topGroup;
  }

  @Nullable
  public Integer getMaxOccurs(String tagName, String namespace) {
    XmlElementsGroup group = findGroup(tagName, namespace);
    if (group == null) {
      return null;
    }
    int result = 1;
    for (; group != null; group = group.getParentGroup()) {
      result *= group.getMaxOccurs();
    }
    return result;
  }

  @Nullable
  public XmlElementsGroup findGroup(final String tagName, final String namespace) {
    final Ref<XmlElementsGroup> result = new Ref<XmlElementsGroup>();
    processGroups(myTopGroup, new Processor<XmlElementsGroup>() {
      @Override
      public boolean process(XmlElementsGroup group) {
        if (group.getGroupType() == XmlElementsGroup.Type.LEAF) {
          XmlElementDescriptor descriptor = group.getLeafDescriptor();
          String ns = descriptor instanceof XmlElementDescriptorImpl ? ((XmlElementDescriptorImpl)descriptor).getNamespace() : "";
          if (tagName.equals(descriptor.getName()) && namespace.equals(ns)) {
            result.set(group);
            return false;
          }
        }
        return true;
      }
    });
    return result.get();
  }

  public static List<XmlElementsGroup> getParentsOfType(XmlElementsGroup group, XmlElementsGroup.Type type) {

    List<XmlElementsGroup> groups = new ArrayList<XmlElementsGroup>();
    while (group != null) {
      if (group.getGroupType() == type) {
        groups.add(group);
      }
      group = group.getParentGroup();
    }
    return groups;
  }

  public static List<XmlElementDescriptor> getAllLeafs(XmlElementsGroup group) {
    final List<XmlElementDescriptor> descriptors = new ArrayList<XmlElementDescriptor>();
    processGroups(group, new Processor<XmlElementsGroup>() {
      @Override
      public boolean process(XmlElementsGroup xmlElementsGroup) {
        if (xmlElementsGroup.getGroupType() == XmlElementsGroup.Type.LEAF) {
          descriptors.add(xmlElementsGroup.getLeafDescriptor());
        }
        return true;
      }
    });
    return descriptors;
  }

  public static boolean processGroups(XmlElementsGroup group, Processor<XmlElementsGroup> processor) {
    if (!processor.process(group)) {
      return false;
    }
    if (group.getGroupType() != XmlElementsGroup.Type.LEAF) {
      for (XmlElementsGroup subGroup : group.getSubGroups()) {
        if (!processGroups(subGroup, processor)) {
          return false;
        }
      }
    }
    return true;
  }

  @Nullable
  public XmlElementsGroup findGroup(XmlTag tag) {
    return findGroup(tag.getLocalName(), tag.getNamespace());
  }

  public static boolean isAncestor(XmlElementsGroup group, XmlElementsGroup ancestor) {
    do {
      if (group == ancestor) return true;
      group = group.getParentGroup();
    } while (group != null);
    return false;
  }
}
