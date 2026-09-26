// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xml.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.PluginAware;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.RequiredElement;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Transient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DomImplementationClassEP implements PluginAware {
  static final ExtensionPointName<DomImplementationClassEP> EP_NAME = new ExtensionPointName<>("com.intellij.dom.implementation");
  static final ExtensionPointName<DomImplementationClassEP> CONVERTER_EP_NAME = new ExtensionPointName<>("com.intellij.dom.converter");

  @RequiredElement
  @Attribute("interfaceClass")
  public String interfaceName;

  @RequiredElement
  @Attribute("implementationClass")
  public String implementationName;
  private PluginDescriptor pluginDescriptor;

  public @Nullable Class<?> getInterfaceClass() {
    try {
      return ApplicationManager.getApplication().loadClass(interfaceName, pluginDescriptor);
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (Throwable e) {
      Logger.getInstance(DomImplementationClassEP.class).error(e);
      return null;
    }
  }

  public @Nullable Class<?> getImplementationClass() {
    try {
      return ApplicationManager.getApplication().loadClass(implementationName, pluginDescriptor);
    }
    catch (ProcessCanceledException e) {
      throw e;
    }
    catch (Throwable e) {
      Logger.getInstance(DomImplementationClassEP.class).error(e);
      return null;
    }
  }

  @Override
  @Transient
  public void setPluginDescriptor(@NotNull PluginDescriptor pluginDescriptor) {
    this.pluginDescriptor = pluginDescriptor;
  }
}
