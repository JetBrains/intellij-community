package com.intellij.uiDesigner.radComponents;

import com.intellij.uiDesigner.XmlWriter;
import com.intellij.uiDesigner.UIFormXmlConstants;

import java.util.ArrayList;
import java.util.List;
import java.util.Collections;

/**
 * @author yole
 */
public class RadButtonGroup {
  public static final RadButtonGroup NEW_GROUP = new RadButtonGroup(null);

  private String myName;
  private List<String> myComponentIds = new ArrayList<String>();

  public RadButtonGroup(final String name) {
    myName = name;
  }

  public void write(final XmlWriter writer) {
    writer.startElement(UIFormXmlConstants.ELEMENT_GROUP);
    writer.addAttribute(UIFormXmlConstants.ATTRIBUTE_NAME, myName);
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
