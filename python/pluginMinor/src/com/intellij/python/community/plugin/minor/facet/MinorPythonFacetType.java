// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.plugin.minor.facet;

import com.intellij.facet.Facet;
import com.intellij.facet.FacetType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.facet.PythonFacetDefaults;
import com.jetbrains.python.parser.icons.PythonParserIcons;
import com.jetbrains.python.sdk.internal.PySdkNamesKt;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;

@ApiStatus.Internal
final class MinorPythonFacetType extends FacetType<MinorPythonFacet, PythonFacetConfiguration> {
  private static final @NonNls String ID = PySdkNamesKt.PYTHON_FACET_ID;

  public static MinorPythonFacetType getInstance() {
    return findInstance(MinorPythonFacetType.class);
  }

  MinorPythonFacetType() {
    super(MinorPythonFacet.ID, ID, PyBundle.message("python.facet.name"));
  }

  @Override
  public PythonFacetConfiguration createDefaultConfiguration() {
    PythonFacetConfiguration result = new PythonFacetConfiguration();
    result.setSdk(PythonFacetDefaults.defaultPythonFacetSdk());
    return result;
  }

  @Override
  public MinorPythonFacet createFacet(@NotNull Module module,
                                      String name,
                                      @NotNull PythonFacetConfiguration configuration,
                                      @Nullable Facet underlyingFacet) {
    return new MinorPythonFacet(this, module, name, configuration, underlyingFacet);
  }

  @Override
  public boolean isSuitableModuleType(ModuleType moduleType) {
    return !(moduleType.getId().equals(PySdkNamesKt.PYTHON_MODULE_ID));
  }

  @Override
  public Icon getIcon() {
    return PythonParserIcons.PythonFile;
  }
}
