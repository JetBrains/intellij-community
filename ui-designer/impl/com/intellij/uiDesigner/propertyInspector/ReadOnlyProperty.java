/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.propertyInspector;

import com.intellij.uiDesigner.radComponents.RadComponent;

/**
 * @author yole
 */
public abstract class ReadOnlyProperty extends Property {
  public ReadOnlyProperty(final Property parent, final String name) {
    super(parent, name);
  }

  public Object getValue(final RadComponent component) {
    return null;
  }

  protected void setValueImpl(final RadComponent component, final Object value) throws Exception {
  }

  public PropertyEditor getEditor() {
    return null;
  }
}
