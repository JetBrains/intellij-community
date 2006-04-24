/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.uiDesigner.UIFormXmlConstants;
import com.intellij.uiDesigner.XmlWriter;
import com.intellij.uiDesigner.propertyInspector.IntrospectedProperty;
import com.intellij.uiDesigner.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.propertyInspector.editors.ListModelEditor;
import com.intellij.uiDesigner.propertyInspector.renderers.LabelPropertyRenderer;
import com.intellij.uiDesigner.radComponents.RadComponent;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.lang.reflect.Method;

/**
 * @author yole
 */
public class IntroListModelProperty extends IntrospectedProperty<String[]> {
  private LabelPropertyRenderer<String[]> myRenderer;
  private ListModelEditor myEditor;
  @NonNls private static final String CLIENT_PROPERTY_KEY_PREFIX = "IntroListModelProperty_";

  public IntroListModelProperty(final String name, final Method readMethod, final Method writeMethod, final boolean storeAsClient) {
    super(name, readMethod, writeMethod, storeAsClient);
  }

  public void write(final String[] value, final XmlWriter writer) {
    for(String s: value) {
      writer.startElement(UIFormXmlConstants.ELEMENT_ITEM);
      writer.addAttribute(UIFormXmlConstants.ATTRIBUTE_VALUE, s);
      writer.endElement();
    }
  }

  @NotNull
  public PropertyRenderer<String[]> getRenderer() {
    if (myRenderer == null) {
      myRenderer = new MyRenderer();
    }
    return myRenderer;
  }

  public PropertyEditor<String[]> getEditor() {
    if (myEditor == null) {
      myEditor = new ListModelEditor(getName());
    }
    return myEditor;
  }

  @Override public String[] getValue(final RadComponent component) {
    final String[] strings = (String[])component.getDelegee().getClientProperty(CLIENT_PROPERTY_KEY_PREFIX + getName());
    if (strings == null) {
      return new String[0];
    }
    return strings;
  }

  @Override protected void setValueImpl(final RadComponent component, final String[] value) throws Exception {
    component.getDelegee().putClientProperty(CLIENT_PROPERTY_KEY_PREFIX + getName(), value);
    DefaultComboBoxModel model = new DefaultComboBoxModel();
    for(String s: value) {
      model.addElement(s);
    }
    invokeSetter(component, model);
  }

  private static class MyRenderer extends LabelPropertyRenderer<String[]> {
    @Override protected void customize(final String[] value) {
      setText(StringUtil.join(value, ", "));
    }
  }
}
