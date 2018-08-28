// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner.i18n;

import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.lw.StringDescriptor;
import com.intellij.uiDesigner.propertyInspector.IntrospectedProperty;
import com.intellij.uiDesigner.radComponents.RadComponent;

/**
 * @author yole
 */
public class I18nizeFormPropertyQuickFix extends I18nizeFormQuickFix {
  private final IntrospectedProperty myProperty;

  public I18nizeFormPropertyQuickFix(final GuiEditor editor, final String name, final RadComponent component,
                                     final IntrospectedProperty property) {
    super(editor, name, component);
    myProperty = property;
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
