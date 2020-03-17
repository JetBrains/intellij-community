// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xml.reflect;

import com.intellij.diagnostic.PluginException;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.AbstractExtensionPointBean;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.RequiredElement;
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
 * @see DomExtender
 */
public class DomExtenderEP extends AbstractExtensionPointBean {

  @ApiStatus.Internal
  public static final ExtensionPointName<DomExtenderEP> EP_NAME = ExtensionPointName.create("com.intellij.dom.extender");

  private static final Logger LOG = Logger.getInstance(DomExtenderEP.class);

  static {
    Application app = ApplicationManager.getApplication();
    EP_NAME.addExtensionPointListener(() -> {
        Throwable trace = new Throwable();
        app.invokeLater(() -> StubIndex.getInstance().forceRebuild(trace), app.getDisposed());
    }, app);
  }

  @RequiredElement
  @Attribute("domClass")
  public String domClassName;

  @RequiredElement
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
