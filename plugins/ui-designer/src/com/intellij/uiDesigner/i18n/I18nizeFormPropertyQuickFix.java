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
package com.intellij.uiDesigner.i18n;

import com.intellij.uiDesigner.propertyInspector.IntrospectedProperty;
import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.i18n.I18nizeFormQuickFix;
import com.intellij.uiDesigner.lw.StringDescriptor;

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

  protected StringDescriptor getStringDescriptorValue() {
    return (StringDescriptor) myProperty.getValue(myComponent);
  }

  protected void setStringDescriptorValue(final StringDescriptor descriptor) throws Exception {
    myProperty.setValue(myComponent, descriptor);
  }
}
