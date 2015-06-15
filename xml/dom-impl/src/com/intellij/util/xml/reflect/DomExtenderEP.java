/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.util.xml.reflect;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.AbstractExtensionPointBean;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.util.xml.impl.DomInvocationHandler;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public class DomExtenderEP extends AbstractExtensionPointBean {

  public static final ExtensionPointName<DomExtenderEP> EP_NAME = ExtensionPointName.create("com.intellij.dom.extender");

  private static final Logger LOG = Logger.getInstance("#com.intellij.util.xml.reflect.DomExtenderEP");

  @Attribute("domClass")
  public String domClassName;
  @Attribute("extenderClass")
  public String extenderClassName;

  private volatile Class<?> myDomClass;
  private volatile DomExtender myExtender;

  @Nullable
  public DomExtensionsRegistrarImpl extend(@NotNull final Project project,
                                           @NotNull final DomInvocationHandler handler,
                                           @Nullable DomExtensionsRegistrarImpl registrar) {
    if (myDomClass == null) {
      try {
        myDomClass = findClass(domClassName);
      }
      catch (Exception e) {
        LOG.error(e);
        return registrar;
      }
    }

    if (!myDomClass.isAssignableFrom(handler.getRawType())) {
      return registrar;
    }


    if (myExtender == null) {
      try {
        myExtender = instantiate(extenderClassName, project.getPicoContainer());
      }
      catch (Exception e) {
        LOG.error(e);
        return registrar;
      }
    }

    if (!myExtender.supportsStubs() && XmlUtil.isStubBuilding()) {
      return registrar;
    }

    if (registrar == null) {
      registrar = new DomExtensionsRegistrarImpl();
    }
    //noinspection unchecked
    myExtender.registerExtensions(handler.getProxy(), registrar);

    return registrar;
  }
}
