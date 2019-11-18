/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.util.xml.impl;

import com.intellij.openapi.extensions.AbstractExtensionPointBean;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public class DomImplementationClassEP extends AbstractExtensionPointBean {
  public static final ExtensionPointName<DomImplementationClassEP> EP_NAME = ExtensionPointName.create("com.intellij.dom.implementation");
  public static final ExtensionPointName<DomImplementationClassEP> CONVERTER_EP_NAME = ExtensionPointName.create("com.intellij.dom.converter");

  @Attribute("interfaceClass")
  public String interfaceName;

  @Attribute("implementationClass")
  public String implementationName;


  @Nullable
  public Class getInterfaceClass() {
    return findClassNoExceptions(interfaceName);
  }

  @Nullable
  public Class getImplementationClass() {
    return findClassNoExceptions(implementationName);
  }
}
