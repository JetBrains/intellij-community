// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner.i18n;

import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.lw.StringDescriptor;
import com.intellij.uiDesigner.radComponents.RadContainer;

/**
 * @author yole
 */
public class I18nizeFormBorderQuickFix extends I18nizeFormQuickFix {
  private final RadContainer myContainer;

  public I18nizeFormBorderQuickFix(final GuiEditor editor, final String name, final RadContainer container) {
    super(editor, name, container);
    myContainer = container;
  }

  @Override
  protected StringDescriptor getStringDescriptorValue() {
    return myContainer.getBorderTitle();
  }

  @Override
  protected void setStringDescriptorValue(final StringDescriptor descriptor) throws Exception {
    myContainer.setBorderTitle(descriptor);
  }
}
