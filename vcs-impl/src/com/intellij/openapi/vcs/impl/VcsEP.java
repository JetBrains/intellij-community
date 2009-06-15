/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.openapi.vcs.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.AbstractExtensionPointBean;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.components.BaseComponent;
import com.intellij.util.xmlb.annotations.Attribute;

/**
 * @author yole
 */
public class VcsEP extends AbstractExtensionPointBean {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.impl.VcsEP");

  public static final ExtensionPointName<VcsEP> EP_NAME = ExtensionPointName.create("com.intellij.vcs");

  // these must be public for scrambling compatibility
  @Attribute("name")
  public String name;
  @Attribute("vcsClass")
  public String vcsClass;
  
  private AbstractVcs myVcs;


  public AbstractVcs getVcs(Project project) {
    if (myVcs == null) {
      try {
        final Class<? extends AbstractVcs> foundClass = findClass(vcsClass);
        final Class<?>[] interfaces = foundClass.getInterfaces();
        for (Class<?> anInterface : interfaces) {
          if (BaseComponent.class.isAssignableFrom(anInterface)) {
            myVcs = project.getComponent(foundClass);
            return myVcs;
          }
        }
        myVcs = instantiate(vcsClass, project.getPicoContainer());
      }
      catch(Exception e) {
        LOG.error(e);
        return null;
      }
    }
    return myVcs;
  }
}