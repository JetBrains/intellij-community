// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner.i18n;

import com.intellij.uiDesigner.lw.StringDescriptor;
import com.intellij.uiDesigner.propertyInspector.IntrospectedProperty;
import com.intellij.uiDesigner.radComponents.RadComponent;

public class FormPropertyStringDescriptorAccessor extends StringDescriptorAccessor {

  private final RadComponent myComponent;
  private final IntrospectedProperty myProperty;

  public FormPropertyStringDescriptorAccessor(final RadComponent component,
                                              final IntrospectedProperty property) {
    myComponent = component;
    myProperty = property;
  }

  @Override
  public RadComponent getComponent() {
    return myComponent;
  }

  @Override
  protected StringDescriptor getStringDescriptorValue() {
    return (StringDescriptor) myProperty.getValue(myComponent);
  }

  @Override
  protected void setStringDescriptorValue(final StringDescriptor descriptor) throws Exception {
    myProperty.setValue(myComponent, descriptor);
  }
}
