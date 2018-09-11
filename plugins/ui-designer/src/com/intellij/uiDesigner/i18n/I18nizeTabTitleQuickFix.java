// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner.i18n;

import com.intellij.uiDesigner.designSurface.GuiEditor;
import com.intellij.uiDesigner.lw.StringDescriptor;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.radComponents.RadTabbedPane;

/**
 * @author yole
 */
public class I18nizeTabTitleQuickFix extends I18nizeFormQuickFix {
  private final String myPropName;

  public I18nizeTabTitleQuickFix(final GuiEditor editor, final String name, final RadComponent component, final String propName) {
    super(editor, name, component);
    myPropName = propName;
  }

  @Override
  protected StringDescriptor getStringDescriptorValue() {
    RadTabbedPane tabbedPane = (RadTabbedPane) myComponent.getParent();
    return tabbedPane.getTabProperty(myComponent, myPropName);
  }

  @Override
  protected void setStringDescriptorValue(final StringDescriptor descriptor) throws Exception {
    RadTabbedPane tabbedPane = (RadTabbedPane) myComponent.getParent();
    tabbedPane.setTabProperty(myComponent, myPropName, descriptor);
  }
}
