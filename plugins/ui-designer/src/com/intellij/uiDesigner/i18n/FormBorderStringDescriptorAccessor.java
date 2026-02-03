// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner.i18n;

import com.intellij.uiDesigner.lw.StringDescriptor;
import com.intellij.uiDesigner.radComponents.RadComponent;
import com.intellij.uiDesigner.radComponents.RadContainer;

class FormBorderStringDescriptorAccessor extends StringDescriptorAccessor {
  private final RadContainer myContainer;

  FormBorderStringDescriptorAccessor(RadContainer container) {
    myContainer = container;
  }

  @Override
  public RadComponent getComponent() {
    return myContainer;
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
