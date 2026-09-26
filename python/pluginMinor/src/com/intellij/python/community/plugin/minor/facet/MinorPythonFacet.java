// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.plugin.minor.facet;

import com.intellij.facet.Facet;
import com.intellij.facet.FacetType;
import com.intellij.facet.FacetTypeId;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.python.community.plugin.impl.facet.PythonFacetUtil;
import com.jetbrains.python.facet.LibraryContributingFacet;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * This facet is intended to be used in the python plugin for IDEs other then IntelliJ IDEA
 *
 * @author traff
 */
@ApiStatus.Internal
public final class MinorPythonFacet extends LibraryContributingFacet<PythonFacetConfiguration> {
  public static final FacetTypeId<MinorPythonFacet> ID = new FacetTypeId<>("python");

  MinorPythonFacet(final @NotNull FacetType facetType, final @NotNull Module module, final @NotNull String name, final @NotNull PythonFacetConfiguration configuration,
                   Facet underlyingFacet) {
    super(facetType, module, name, configuration, underlyingFacet);
  }

  @Override
  public void updateLibrary() {
    // Keep ModuleRootManager.sdk in sync with the facet SDK so that components
    // reading ModuleRootManager directly (e.g. run configurations using
    // "Use SDK of module") always see the current Python interpreter.
    // Cannot use `ModuleRootModificationUtil.setModuleSdk(getModule(), getConfiguration().getSdk())`
    // due to InvokeAndWait commit inside, so repeat the PythonFacetUtil.updateLibrary approach
    Module module = getModule();
    PythonFacetConfiguration facetConfiguration = getConfiguration();

    ApplicationManager.getApplication().runWriteAction(() -> {
      ModifiableRootModel model = ModuleRootManager.getInstance(module).getModifiableModel();
      Sdk configSdk = facetConfiguration.getSdk();
      if (model.getSdk() != configSdk) {
        model.setSdk(configSdk);
        model.commit();
      }
      else {
        model.dispose();
      }
    });

    PythonFacetUtil.updateLibrary(module, facetConfiguration);
  }

  @Override
  public void removeLibrary() {
    PythonFacetUtil.removeLibrary(getModule());
  }

  @Override
  public void initFacet() {
    updateLibrary();
  }
}
