// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner.i18n;

import com.intellij.uiDesigner.lw.StringDescriptor;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.radComponents.RadTabbedPane;

public class TabTitleStringDescriptorAccessor extends StringDescriptorAccessor {
  private final RadComponent myComponent;
  private final String myPropName;

  public TabTitleStringDescriptorAccessor(final RadComponent tab,
                                          final String propName) {
    myComponent = tab;
    myPropName = propName;
  }

  @Override
  public RadComponent getComponent() {
    return myComponent;
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
