/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.openapi.vcs.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.PluginAware;
import com.intellij.openapi.extensions.PluginDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import org.picocontainer.defaults.ConstructorInjectionComponentAdapter;

/**
 * @author yole
 */
public class VcsEP implements PluginAware {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.impl.VcsEP");

  public static final ExtensionPointName<VcsEP> EP_NAME = ExtensionPointName.create("com.intellij.vcs");

  // these must be public for scrambling compatibility
  public String name;
  public String vcsClass;
  
  private AbstractVcs myVcs;
  private PluginDescriptor myPluginDescriptor;

  public String getName() {
    return name;
  }

  public void setName(final String name) {
    this.name = name;
  }

  public String getVcsClass() {
    return vcsClass;
  }

  public void setVcsClass(final String vcsClass) {
    this.vcsClass = vcsClass;
  }

  public void setPluginDescriptor(PluginDescriptor pluginDescriptor) {
    myPluginDescriptor = pluginDescriptor;
  }

  public AbstractVcs getVcs(Project project) {
    if (myVcs == null) {
      try {
        final Class<?> aClass = Class.forName(vcsClass, true,
                                              myPluginDescriptor == null ? getClass().getClassLoader()  : myPluginDescriptor.getPluginClassLoader());
        myVcs = (AbstractVcs) new ConstructorInjectionComponentAdapter(vcsClass, aClass).getComponentInstance(project.getPicoContainer());
      }
      catch(Exception e) {
        LOG.error(e);
        return null;
      }
    }
    return myVcs;
  }
}