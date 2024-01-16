// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.plugin.minor.facet;

import com.intellij.facet.Facet;
import com.intellij.facet.FacetType;
import com.intellij.facet.FacetTypeId;
import com.intellij.openapi.module.Module;
import com.jetbrains.python.facet.LibraryContributingFacet;
import com.intellij.python.community.plugin.impl.facet.PythonFacetUtil;
import org.jetbrains.annotations.NotNull;

/**
 * This facet is intended to be used in the python plugin for IDEs other then IntelliJ IDEA
 *
 * @author traff
 */
class MinorPythonFacet extends LibraryContributingFacet<MinorPythonFacetType.PythonFacetConfiguration> {
  public static final FacetTypeId<MinorPythonFacet> ID = new FacetTypeId<>("python");

  MinorPythonFacet(@NotNull final FacetType facetType, @NotNull final Module module, @NotNull final String name, @NotNull final MinorPythonFacetType.PythonFacetConfiguration configuration,
                          Facet underlyingFacet) {
    super(facetType, module, name, configuration, underlyingFacet);
  }

  @Override
  public void updateLibrary() {
    PythonFacetUtil.updateLibrary(getModule(), getConfiguration());
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
