// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.radComponents;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.uiDesigner.UIFormXmlConstants;
import com.intellij.uiDesigner.XmlWriter;
import com.intellij.uiDesigner.lw.IButtonGroup;
import com.intellij.util.ArrayUtilRt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


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

  @Override
  public @NlsSafe String getName() {
    return myName;
  }

  public void setName(final String name) {
    myName = name;
  }

  @Override
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

  @Override
  public String[] getComponentIds() {
    return ArrayUtilRt.toStringArray(myComponentIds);
  }

  public boolean isEmpty() {
    return myComponentIds.isEmpty();
  }
}
