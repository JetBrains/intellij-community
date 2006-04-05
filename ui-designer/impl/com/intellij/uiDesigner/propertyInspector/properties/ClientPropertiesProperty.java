/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.propertyInspector.properties;

import com.intellij.uiDesigner.propertyInspector.Property;
import com.intellij.uiDesigner.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.propertyInspector.renderers.LabelPropertyRenderer;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.ClientPropertiesManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class ClientPropertiesProperty extends Property {
  public static ClientPropertiesProperty getInstance(Project project) {
    return project.getComponent(ClientPropertiesProperty.class);
  }

  private PropertyRenderer myRenderer = new LabelPropertyRenderer() {
    @Override protected void customize(final Object value) {
      setText("");
    }
  };

  public ClientPropertiesProperty() {
    super(null, "Client Properties");
  }

  public Object getValue(final RadComponent component) {
    return null;
  }

  protected void setValueImpl(final RadComponent component, final Object value) throws Exception {
  }

  @NotNull
  public PropertyRenderer getRenderer() {
    return myRenderer;
  }

  public PropertyEditor getEditor() {
    return null;
  }

  @NotNull @Override
  public Property[] getChildren(final RadComponent component) {
    ClientPropertiesManager manager = ClientPropertiesManager.getInstance(component.getProject());
    ClientPropertiesManager.ClientProperty[] props = manager.getClientProperties(component.getComponentClass());
    Property[] result = new Property[props.length];
    for(int i=0; i<props.length; i++) {
      result [i] = new ClientPropertyProperty(this, props [i].getName(), props [i].getValueClass());
    }
    return result;
  }
}
