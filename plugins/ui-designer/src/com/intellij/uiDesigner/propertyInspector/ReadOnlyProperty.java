// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.uiDesigner.propertyInspector;

import com.intellij.uiDesigner.radComponents.RadComponent;
import org.jetbrains.annotations.NonNls;

/**
 * @author yole
 */
public abstract class ReadOnlyProperty extends Property {
  public ReadOnlyProperty(final Property parent, @NonNls final String name) {
    super(parent, name);
  }

  @Override
  public Object getValue(final RadComponent component) {
    return null;
  }

  @Override
  protected void setValueImpl(final RadComponent component, final Object value) throws Exception {
  }

  @Override
  public PropertyEditor getEditor() {
    return null;
  }
}
