/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.jetbrains.python.minor.facet;

import com.intellij.facet.Facet;
import com.intellij.facet.FacetType;
import com.intellij.facet.FacetTypeId;
import com.intellij.openapi.module.Module;
import com.jetbrains.python.facet.LibraryContributingFacet;
import com.jetbrains.python.facet.PythonFacetUtil;
import org.jetbrains.annotations.NotNull;

/**
 * This facet is intended to be used in the python plugin for IDEs other then IntelliJ IDEA
 *
 * @author traff
 */
public class PythonFacet extends LibraryContributingFacet<PythonFacetType.PythonFacetConfiguration> {
  public static final FacetTypeId<PythonFacet> ID = new FacetTypeId<>("python");

  public PythonFacet(@NotNull final FacetType facetType, @NotNull final Module module, @NotNull final String name, @NotNull final PythonFacetType.PythonFacetConfiguration configuration,
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
