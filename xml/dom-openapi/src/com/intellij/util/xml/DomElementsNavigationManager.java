// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.util.xml;

import com.intellij.openapi.project.Project;

import java.util.Set;

public abstract class DomElementsNavigationManager {
  public static String DEFAULT_PROVIDER_NAME = "DEFAULT_PROVIDER_NAME";

  public static DomElementsNavigationManager getManager(Project project) {
    return project.getService(DomElementsNavigationManager.class);
  }

  public abstract Set<DomElementNavigationProvider> getDomElementsNavigateProviders(DomElement domElement);

  public abstract DomElementNavigationProvider getDomElementsNavigateProvider(String providerName);

  public abstract void registerDomElementsNavigateProvider(DomElementNavigationProvider provider);


}
