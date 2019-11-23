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

import com.intellij.diagnostic.PluginException;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.AbstractExtensionPointBean;
import com.intellij.openapi.extensions.ExtensionPointListener;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.util.xml.impl.DomInvocationHandler;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public class DomExtenderEP extends AbstractExtensionPointBean {

  @ApiStatus.Internal
  public static final ExtensionPointName<DomExtenderEP> EP_NAME = ExtensionPointName.create("com.intellij.dom.extender");

  private static final Logger LOG = Logger.getInstance(DomExtenderEP.class);

  static {
    EP_NAME.addExtensionPointListener(new ExtensionPointListener<DomExtenderEP>() {
      @Override
      public void extensionAdded(@NotNull DomExtenderEP extension, @NotNull PluginDescriptor pluginDescriptor) {
        StubIndex.getInstance().forceRebuild(new Throwable());
      }

      @Override
      public void extensionRemoved(@NotNull DomExtenderEP extension, @NotNull PluginDescriptor pluginDescriptor) {
        StubIndex.getInstance().forceRebuild(new Throwable());
      }
    }, ApplicationManager.getApplication());
  }

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
      myDomClass = findClassNoExceptions(domClassName);
      if (myDomClass == null) {
        return registrar;
      }
    }

    if (!myDomClass.isAssignableFrom(handler.getRawType())) {
      return registrar;
    }


    if (myExtender == null) {
      try {
        myExtender = instantiateClass(extenderClassName, project.getPicoContainer());
      }
      catch (Throwable e) {
        LOG.error(new PluginException(e, getPluginId()));
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
