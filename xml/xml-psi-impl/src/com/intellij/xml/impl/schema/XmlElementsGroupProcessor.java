// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xml.impl.schema;

import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.XmlElementsGroup;

import java.util.Stack;

/**
 * @author Dmitry Avdeev
 */
public final class XmlElementsGroupProcessor extends XmlSchemaTagsProcessor {

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
