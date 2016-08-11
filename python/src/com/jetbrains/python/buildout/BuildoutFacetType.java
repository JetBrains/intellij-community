/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.buildout;

import com.intellij.facet.Facet;
import com.intellij.facet.FacetType;
import com.intellij.facet.FacetTypeId;
import com.intellij.facet.FacetTypeRegistry;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.jetbrains.python.PythonModuleTypeBase;
import icons.PythonIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Describes the buildout facet.
 * User: dcheryasov
 * Date: Jul 26, 2010 5:47:24 PM
 */
public class BuildoutFacetType extends FacetType<BuildoutFacet, BuildoutFacetConfiguration> {
  private BuildoutFacetType() {
    super(ID, "buildout-python", "Buildout Support", null);
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
    BuildoutFacet facet = new BuildoutFacet(this, module, name, configuration, underlyingFacet);
    return facet;
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
