// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.xml.reflect;

import com.intellij.diagnostic.PluginException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.PluginAware;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.extensions.RequiredElement;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.impl.DomInvocationHandler;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Transient;
import com.intellij.xml.util.XmlUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 * @see DomExtender
 */
public final class DomExtenderEP implements PluginAware {
  @ApiStatus.Internal
  public static final ExtensionPointName<DomExtenderEP> EP_NAME = new ExtensionPointName<>("com.intellij.dom.extender");

  private static final Logger LOG = Logger.getInstance(DomExtenderEP.class);

  private PluginDescriptor pluginDescriptor;

  private DomExtenderEP() {
  }

  public DomExtenderEP(@NotNull String domClassName, @NotNull PluginDescriptor pluginDescriptor) {
    this.domClassName = domClassName;
    this.pluginDescriptor = pluginDescriptor;
  }

  @RequiredElement
  @Attribute("domClass")
  public String domClassName;

  @RequiredElement
  @Attribute("extenderClass")
  public String extenderClassName;

  private volatile Class<?> myDomClass;
  private volatile DomExtender<?> myExtender;

  @Nullable
  public DomExtensionsRegistrarImpl extend(@NotNull Project project,
                                           @NotNull DomInvocationHandler handler,
                                           @Nullable DomExtensionsRegistrarImpl registrar) {
    if (myDomClass == null) {
      try {
        myDomClass = Class.forName(domClassName, true, pluginDescriptor.getPluginClassLoader());
      }
      catch (Throwable e) {
        LOG.error(new PluginException(e, pluginDescriptor.getPluginId()));
        return registrar;
      }
    }

    if (!myDomClass.isAssignableFrom(handler.getRawType())) {
      return registrar;
    }

    if (myExtender == null) {
      try {
        myExtender = project.instantiateExtensionWithPicoContainerOnlyIfNeeded(extenderClassName, pluginDescriptor);
      }
      catch (ProcessCanceledException e) {
        throw e;
      }
      catch (Throwable e) {
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
    ((DomExtender<DomElement>)myExtender).registerExtensions(handler.getProxy(), registrar);
    return registrar;
  }

  @Override
  @Transient
  public void setPluginDescriptor(@NotNull PluginDescriptor pluginDescriptor) {
    this.pluginDescriptor = pluginDescriptor;
  }
}
