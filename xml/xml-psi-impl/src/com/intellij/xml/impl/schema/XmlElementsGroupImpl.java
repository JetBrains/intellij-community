// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xml.impl.schema;

import com.intellij.psi.xml.XmlTag;
import com.intellij.xml.XmlElementDescriptor;
import com.intellij.xml.XmlElementsGroup;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Dmitry Avdeev
 */
public class XmlElementsGroupImpl extends XmlElementsGroupBase {

  private static final Map<String, XmlElementsGroup.Type> TYPES = new HashMap<>();
  static {
    TYPES.put("sequence", XmlElementsGroup.Type.SEQUENCE);
    TYPES.put("choice", XmlElementsGroup.Type.CHOICE);
    TYPES.put("all", XmlElementsGroup.Type.ALL);
    TYPES.put("group", XmlElementsGroup.Type.GROUP);
  }

  private final List<XmlElementsGroup> mySubGroups = new ArrayList<>();

  public XmlElementsGroupImpl(XmlTag tag, XmlElementsGroup parent, XmlTag ref) {
    super(tag, parent, ref);
  }

  @Override
  public XmlElementsGroup.Type getGroupType() {
    return getTagType(myTag);
  }

  public static XmlElementsGroup.Type getTagType(XmlTag tag) {
    return TYPES.get(tag.getLocalName());
  }

  @Override
  public List<XmlElementsGroup> getSubGroups() {
    return mySubGroups;
  }

  @Override
  public XmlElementDescriptor getLeafDescriptor() {
    throw new RuntimeException("not a leaf group");
  }

  public void addSubGroup(XmlElementsGroup group) {
    mySubGroups.add(group);
  }

  @Override
  public String toString() {
    return getGroupType().toString();
  }
}
