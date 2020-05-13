// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.uiDesigner.i18n;

import com.intellij.uiDesigner.lw.StringDescriptor;
import com.intellij.uiDesigner.radComponents.RadComponent;

public abstract class StringDescriptorAccessor {

  public abstract RadComponent getComponent();

  protected abstract StringDescriptor getStringDescriptorValue();
  protected abstract void setStringDescriptorValue(final StringDescriptor descriptor) throws Exception;
}
