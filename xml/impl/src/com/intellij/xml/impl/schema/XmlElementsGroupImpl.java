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

  private final static Map<String, Type> TYPES = new HashMap<String, Type>();
  static {
    TYPES.put("sequence", Type.SEQUENCE);
    TYPES.put("choice", Type.CHOICE);
    TYPES.put("all", Type.ALL);
    TYPES.put("group", Type.GROUP);
  }

  private final List<XmlElementsGroup> mySubGroups = new ArrayList<XmlElementsGroup>();

  public XmlElementsGroupImpl(XmlTag tag, XmlElementsGroup parent) {
    super(tag, parent);
  }

  @Override
  public Type getGroupType() {
    return getTagType(myTag);
  }

  public static Type getTagType(XmlTag tag) {
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
}
