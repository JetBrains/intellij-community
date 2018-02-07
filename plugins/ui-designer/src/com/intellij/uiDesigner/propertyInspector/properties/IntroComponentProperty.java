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
import com.intellij.uiDesigner.snapShooter.SnapshotContext;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Method;

/**
 * The value of the property is the string ID of the referenced component.
 * @author yole
 */
public class IntroComponentProperty extends IntrospectedProperty<String> {
  private final ComponentRenderer myRenderer = new ComponentRenderer();
  private ComponentEditor myEditor;
  @NonNls private static final String CLIENT_PROPERTY_KEY_PREFIX = "IntroComponentProperty_";
  private final Class myPropertyType;
  private final Condition<RadComponent> myFilter;

  public IntroComponentProperty(String name,
                                Method readMethod,
                                Method writeMethod,
                                Class propertyType,
                                Condition<RadComponent> filter,
                                final boolean storeAsClient) {
    super(name, readMethod, writeMethod, storeAsClient);
    myPropertyType = propertyType;
    myFilter = filter;
  }

  @NotNull public PropertyRenderer<String> getRenderer() {
    return myRenderer;
  }

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

  @Override public void importSnapshotValue(final SnapshotContext context, final JComponent component, final RadComponent radComponent) {
    Component value;
    try {
      value = (Component) myReadMethod.invoke(component, EMPTY_OBJECT_ARRAY);
    }
    catch (Exception e) {
      return;
    }
    if (value instanceof JComponent) {
      context.registerComponentProperty(component, getName(), (JComponent) value);
    }
  }
}
