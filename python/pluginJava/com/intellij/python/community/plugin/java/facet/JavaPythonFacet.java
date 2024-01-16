// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.plugin.java.facet;

import com.intellij.facet.Facet;
import com.intellij.facet.FacetType;
import com.intellij.facet.FacetTypeId;
import com.intellij.openapi.module.Module;
import com.intellij.python.community.plugin.impl.facet.PythonFacetUtil;
import com.jetbrains.python.facet.LibraryContributingFacet;
import org.jetbrains.annotations.NotNull;


public class JavaPythonFacet extends LibraryContributingFacet<JavaPythonFacetConfiguration> {
  public static final FacetTypeId<JavaPythonFacet> ID = new FacetTypeId<>("python");

  public JavaPythonFacet(@NotNull final FacetType facetType,
                         @NotNull final Module module,
                         @NotNull final String name,
                         @NotNull final JavaPythonFacetConfiguration configuration,
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
