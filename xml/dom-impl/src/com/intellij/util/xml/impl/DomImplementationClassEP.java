// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xml.impl;

import com.intellij.openapi.extensions.AbstractExtensionPointBean;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public class DomImplementationClassEP extends AbstractExtensionPointBean {
  static final ExtensionPointName<DomImplementationClassEP> EP_NAME = new ExtensionPointName<>("com.intellij.dom.implementation");
  static final ExtensionPointName<DomImplementationClassEP> CONVERTER_EP_NAME = new ExtensionPointName<>("com.intellij.dom.converter");

  @Attribute("interfaceClass")
  public String interfaceName;

  @Attribute("implementationClass")
  public String implementationName;


  @Nullable
  public Class<?> getInterfaceClass() {
    return findClassNoExceptions(interfaceName);
  }

  @Nullable
  public Class<?> getImplementationClass() {
    return findClassNoExceptions(implementationName);
  }
}
