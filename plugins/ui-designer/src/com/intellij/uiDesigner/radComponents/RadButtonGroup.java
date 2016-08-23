/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.uiDesigner.radComponents;

import com.intellij.uiDesigner.XmlWriter;
import com.intellij.uiDesigner.UIFormXmlConstants;
import com.intellij.uiDesigner.lw.IButtonGroup;
import com.intellij.util.ArrayUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Collections;

/**
 * @author yole
 */
public class RadButtonGroup implements IButtonGroup {
  public static final RadButtonGroup NEW_GROUP = new RadButtonGroup(null);

  private String myName;
  private final List<String> myComponentIds = new ArrayList<>();
  private boolean myBound;

  public RadButtonGroup(final String name) {
    myName = name;
  }

  public void write(final XmlWriter writer) {
    writer.startElement(UIFormXmlConstants.ELEMENT_GROUP);
    writer.addAttribute(UIFormXmlConstants.ATTRIBUTE_NAME, myName);
    if (myBound) {
      writer.addAttribute(UIFormXmlConstants.ATTRIBUTE_BOUND, true);
    }
    for(String id: myComponentIds) {
      writer.startElement(UIFormXmlConstants.ELEMENT_MEMBER);
      writer.addAttribute(UIFormXmlConstants.ATTRIBUTE_ID, id);
      writer.endElement();
    }
    writer.endElement();
  }

  public boolean contains(final RadComponent component) {
    return myComponentIds.contains(component.getId());
  }

  public String getName() {
    return myName;
  }

  public void setName(final String name) {
    myName = name;
  }

  public boolean isBound() {
    return myBound;
  }

  public void setBound(final boolean bound) {
    myBound = bound;
  }

  public void add(final RadComponent component) {
    myComponentIds.add(component.getId());
  }

  public void remove(final RadComponent component) {
    myComponentIds.remove(component.getId());
  }

  public void addComponentIds(final String[] componentIds) {
    Collections.addAll(myComponentIds, componentIds);
  }

  public String[] getComponentIds() {
    return ArrayUtil.toStringArray(myComponentIds);
  }

  public boolean isEmpty() {
    return myComponentIds.size() == 0;
  }
}
