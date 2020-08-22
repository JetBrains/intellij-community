// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.buildout;

import com.intellij.facet.Facet;
import com.intellij.facet.FacetType;
import com.intellij.facet.FacetTypeId;
import com.intellij.facet.FacetTypeRegistry;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.PythonModuleTypeBase;
import icons.PythonIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Describes the buildout facet.
 */
public final class BuildoutFacetType extends FacetType<BuildoutFacet, BuildoutFacetConfiguration> {
  private BuildoutFacetType() {
    super(ID, "buildout-python", PyBundle.message("buildout.facet.title"));
  }

  @Override
  public BuildoutFacetConfiguration createDefaultConfiguration() {
    return new BuildoutFacetConfiguration(null);
  }

  @Override
  public BuildoutFacet createFacet(@NotNull Module module,
                                   String name,
                                   @NotNull BuildoutFacetConfiguration configuration,
                                   @Nullable Facet underlyingFacet) {
    return new BuildoutFacet(this, module, name, configuration, underlyingFacet);
  }

  @Override
  public boolean isSuitableModuleType(ModuleType moduleType) {
    return moduleType instanceof PythonModuleTypeBase;
  }

  public static final FacetTypeId<BuildoutFacet> ID = new FacetTypeId<>("buildout-python");

  public static BuildoutFacetType getInstance() {
    return (BuildoutFacetType)FacetTypeRegistry.getInstance().findFacetType(ID);
  }

  @Override
  public Icon getIcon() {
    return PythonIcons.Python.Buildout.Buildout;
  }
}
