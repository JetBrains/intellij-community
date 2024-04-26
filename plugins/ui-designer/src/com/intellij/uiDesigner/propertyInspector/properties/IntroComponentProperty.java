// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.openapi.util.Condition;
import com.intellij.uiDesigner.FormEditingUtil;
import com.intellij.uiDesigner.SwingProperties;
import com.intellij.uiDesigner.inspections.FormInspectionUtil;
import com.intellij.uiDesigner.propertyInspector.IntrospectedProperty;
import com.intellij.uiDesigner.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.propertyInspector.editors.ComponentEditor;
import com.intellij.uiDesigner.propertyInspector.renderers.ComponentRenderer;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.radComponents.RadRootContainer;
import com.intellij.uiDesigner.radComponents.RadScrollPane;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;

/**
 * The value of the property is the string ID of the referenced component.
 */
public class IntroComponentProperty extends IntrospectedProperty<String> {
  private final ComponentRenderer myRenderer = new ComponentRenderer();
  private ComponentEditor myEditor;
  private static final @NonNls String CLIENT_PROPERTY_KEY_PREFIX = "IntroComponentProperty_";
  private final Class myPropertyType;
  private final Condition<? super RadComponent> myFilter;

  public IntroComponentProperty(String name,
                                Method readMethod,
                                Method writeMethod,
                                Class propertyType,
                                Condition<? super RadComponent> filter,
                                final boolean storeAsClient) {
    super(name, readMethod, writeMethod, storeAsClient);
    myPropertyType = propertyType;
    myFilter = filter;
  }

  @Override
  public @NotNull PropertyRenderer<String> getRenderer() {
    return myRenderer;
  }

  @Override
  public PropertyEditor<String> getEditor() {
    if (myEditor == null) {
      myEditor = new ComponentEditor(myPropertyType, myFilter);
    }
    return myEditor;
  }

  @Override public String getValue(final RadComponent component) {
    return (String) component.getDelegee().getClientProperty(CLIENT_PROPERTY_KEY_PREFIX + getName());
  }

  @Override protected void setValueImpl(final RadComponent component, final String value) throws Exception {
    component.getDelegee().putClientProperty(CLIENT_PROPERTY_KEY_PREFIX + getName(), value);
    if (getName().equals(SwingProperties.LABEL_FOR) && !component.isLoadingProperties() && component.getModule() != null) {
      updateLabelForBinding(component);
    }
  }

  void updateLabelForBinding(final RadComponent component) {
    String value = getValue(component);
    String text = FormInspectionUtil.getText(component.getModule(), component);
    if (text != null && value != null) {
      RadRootContainer root = (RadRootContainer) FormEditingUtil.getRoot(component);
      if (root != null) {
        RadComponent valueComponent = (RadComponent)FormEditingUtil.findComponent(root, value);
        if (valueComponent != null) {
          if (valueComponent instanceof RadScrollPane && ((RadScrollPane) valueComponent).getComponentCount() == 1) {
            valueComponent = ((RadScrollPane) valueComponent).getComponent(0);
          }
          BindingProperty.checkCreateBindingFromText(valueComponent, text);
        }
      }
    }
  }

  @Override public void resetValue(RadComponent component) throws Exception {
    setValue(component, null);
    markTopmostModified(component, false);
  }
}
