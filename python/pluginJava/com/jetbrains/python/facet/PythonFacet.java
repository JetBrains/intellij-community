// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.facet;

import com.intellij.facet.Facet;
import com.intellij.facet.FacetType;
import com.intellij.facet.FacetTypeId;
import com.intellij.openapi.module.Module;
import com.intellij.python.community.plugin.impl.facet.PythonFacetUtil;
import org.jetbrains.annotations.NotNull;


public class PythonFacet extends LibraryContributingFacet<PythonFacetConfiguration> {
  public static final FacetTypeId<PythonFacet> ID = new FacetTypeId<>("python");

  public PythonFacet(@NotNull final FacetType facetType,
                     @NotNull final Module module,
                     @NotNull final String name,
                     @NotNull final PythonFacetConfiguration configuration,
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

  public static String getFacetLibraryName(final String sdkName) {
    return sdkName + PYTHON_FACET_LIBRARY_NAME_SUFFIX;
  }

  @Override
  public void initFacet() {
    updateLibrary();
  }
}
