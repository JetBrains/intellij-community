/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.project;

import com.intellij.facet.Facet;
import com.intellij.facet.FacetType;
import com.intellij.facet.FacetTypeId;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene Zhuravlev
 *         Date: 10/19/12
 */
public class MavenFacetType extends FacetType<MavenFacet, MavenFacetConfiguration>{
  public final static FacetTypeId<MavenFacet> ID = new FacetTypeId<MavenFacet>("_maven_");

  public MavenFacetType() {
    super(ID, ID.toString(), "Maven");
  }

  @Override
  public MavenFacetConfiguration createDefaultConfiguration() {
    return new MavenFacetConfiguration();
  }

  @Override
  public MavenFacet createFacet(@NotNull Module module, String name, @NotNull MavenFacetConfiguration configuration, @Nullable Facet underlyingFacet) {
    return new MavenFacet(this, module, name, configuration, underlyingFacet);
  }

  @Override
  public boolean isSuitableModuleType(ModuleType moduleType) {
    return moduleType instanceof JavaModuleType;
  }


  public static MavenFacetType getInstance() {
    return Holder.ourInstance;
  }

  private static class Holder {
    static final MavenFacetType ourInstance = FacetType.findInstance(MavenFacetType.class);
  }
}
