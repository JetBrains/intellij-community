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

import com.intellij.uiDesigner.propertyInspector.Property;
import com.intellij.uiDesigner.propertyInspector.PropertyEditor;
import com.intellij.uiDesigner.propertyInspector.PropertyRenderer;
import com.intellij.uiDesigner.propertyInspector.editors.*;
import com.intellij.uiDesigner.propertyInspector.renderers.BooleanRenderer;
import com.intellij.uiDesigner.propertyInspector.renderers.LabelPropertyRenderer;
import com.intellij.uiDesigner.radComponents.RadComponent;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class ClientPropertyProperty extends Property {
  private final PropertyRenderer myRenderer;
  private PropertyEditor myEditor;

  public ClientPropertyProperty(final Property parent, final String name, final String valueClass) {
    super(parent, name);
    if (valueClass.equals(Boolean.class.getName())) {
      myRenderer = new BooleanRenderer();
      myEditor = new BooleanEditor();
    }
    else if (valueClass.equals(Double.class.getName())) {
      myRenderer = new LabelPropertyRenderer();
      myEditor = new PrimitiveTypeEditor(Double.class);
    }
    else {
      myRenderer = new LabelPropertyRenderer();
      if (valueClass.equals(Integer.class.getName())) {
        myEditor = new IntEditor(Integer.MIN_VALUE);
      }
      else if (valueClass.equals(String.class.getName())) {
        myEditor = new AbstractTextFieldEditor<String>() {
          public String getValue() throws Exception {
            return myTf.getText();
          }
        };
      }
    }
  }

  public Object getValue(final RadComponent component) {
    return component.getDelegee().getClientProperty(getName());
  }

  protected void setValueImpl(final RadComponent component, final Object value) throws Exception {
    component.getDelegee().putClientProperty(getName(), value);
  }

  @Override public boolean isModified(final RadComponent component) {
    return getValue(component) != null;
  }

  @Override public void resetValue(final RadComponent component) throws Exception {
    component.getDelegee().putClientProperty(getName(), null);
  }

  @NotNull
  public PropertyRenderer getRenderer() {
    return myRenderer;
  }

  public PropertyEditor getEditor() {
    return myEditor;
  }
}
