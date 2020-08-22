// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.maven.model.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.BuildTargetLoader;
import org.jetbrains.jps.builders.ModuleBasedBuildTargetType;
import org.jetbrains.jps.maven.model.JpsMavenExtensionService;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.module.JpsModule;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
* @author Eugene Zhuravlev
*/
public final class MavenResourcesTargetType extends ModuleBasedBuildTargetType<MavenResourcesTarget> {
  public static final MavenResourcesTargetType PRODUCTION = new MavenResourcesTargetType("maven-resources-production", false);
  public static final MavenResourcesTargetType TEST = new MavenResourcesTargetType("maven-resources-test", true);

  private final boolean myIsTests;

  private MavenResourcesTargetType(final String typeId, boolean isTests) {
    super(typeId, true);
    myIsTests = isTests;
  }

  public boolean isTests() {
    return myIsTests;
  }

  @NotNull
  @Override
  public List<MavenResourcesTarget> computeAllTargets(@NotNull JpsModel model) {
    final List<MavenResourcesTarget> targets = new ArrayList<>();
    final JpsMavenExtensionService service = JpsMavenExtensionService.getInstance();
    for (JpsModule module : model.getProject().getModules()) {
      if (service.getExtension(module) != null) {
        targets.add(new MavenResourcesTarget(this, module));
      }
    }
    return targets;
  }

  @NotNull
  @Override
  public BuildTargetLoader<MavenResourcesTarget> createLoader(@NotNull JpsModel model) {
    final Map<String, JpsModule> modules = new HashMap<>();
    for (JpsModule module : model.getProject().getModules()) {
      modules.put(module.getName(), module);
    }
    return new BuildTargetLoader<MavenResourcesTarget>() {
      @Nullable
      @Override
      public MavenResourcesTarget createTarget(@NotNull String targetId) {
        final JpsModule module = modules.get(targetId);
        return module != null ? new MavenResourcesTarget(MavenResourcesTargetType.this, module) : null;
      }
    };
  }
}
