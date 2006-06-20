package com.intellij.uiDesigner.radComponents;

import com.intellij.uiDesigner.XmlWriter;
import com.intellij.uiDesigner.UIFormXmlConstants;
import com.intellij.uiDesigner.lw.IButtonGroup;

import java.util.ArrayList;
import java.util.List;
import java.util.Collections;

/**
 * @author yole
 */
public class RadButtonGroup implements IButtonGroup {
  public static final RadButtonGroup NEW_GROUP = new RadButtonGroup(null);

  private String myName;
  private List<String> myComponentIds = new ArrayList<String>();
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
    return myComponentIds.toArray(new String[myComponentIds.size()]);
  }
}
