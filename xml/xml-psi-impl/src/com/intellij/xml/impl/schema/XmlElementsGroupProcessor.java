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
package com.intellij.xml.impl.schema;

import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.XmlElementsGroup;

import java.util.Stack;

/**
 * @author Dmitry Avdeev
 */
public class XmlElementsGroupProcessor extends XmlSchemaTagsProcessor {

  final Stack<XmlElementsGroup> myGroups = new Stack<>();

  public static XmlElementsGroup computeGroups(XmlNSDescriptorImpl descriptor, XmlTag tag) {
    XmlElementsGroupProcessor processor = new XmlElementsGroupProcessor(descriptor);
    processor.startProcessing(tag);
    return processor.getRootGroup();
  }

  private XmlElementsGroup getRootGroup() {
    return myGroups.get(0);
  }

  private XmlElementsGroupProcessor(XmlNSDescriptorImpl nsDescriptor) {
    super(nsDescriptor, "attribute");
    myGroups.push(new XmlElementsGroupImpl(null, null, null) {
      @Override
      public XmlElementsGroup.Type getGroupType() {
        return XmlElementsGroup.Type.GROUP;
      }

      @Override
      public String toString() {
        return "root";
      }
    });
  }

  @Override
  protected void tagStarted(XmlTag tag, String tagName, XmlTag context, XmlTag ref) {
    XmlElementsGroup.Type type = XmlElementsGroupImpl.getTagType(tag);
    if (type != null) {
      XmlElementsGroupImpl group = new XmlElementsGroupImpl(tag, myGroups.peek(), ref);
      addSubGroup(group);
      myGroups.push(group);
    }
    else if ("element".equals(tagName)) {
      XmlElementsGroup group = new XmlElementsGroupLeaf(tag, myNsDescriptor.createElementDescriptor(tag), myGroups.peek(), ref);
      if (!myGroups.empty()) {
        addSubGroup(group);
      }
      else {
        myGroups.push(group);
      }
    }
  }

  @Override
  protected void tagFinished(XmlTag tag) {
    if (!myGroups.empty() && XmlElementsGroupImpl.getTagType(tag) != null) {
      myGroups.pop();
    }
  }

  private void addSubGroup(XmlElementsGroup group) {
    if (!myGroups.empty()) {
      XmlElementsGroup last = myGroups.peek();
      if (last instanceof XmlElementsGroupImpl) {
        ((XmlElementsGroupImpl)last).addSubGroup(group);
      }
    }
  }
}
