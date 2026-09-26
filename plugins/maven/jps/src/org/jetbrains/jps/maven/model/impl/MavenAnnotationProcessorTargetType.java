// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.maven.model.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.BuildTargetLoader;
import org.jetbrains.jps.builders.ModuleBasedBuildTargetType;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.module.JpsModule;

import java.util.ArrayList;
import java.util.List;

public final class MavenAnnotationProcessorTargetType extends ModuleBasedBuildTargetType<MavenAnnotationProcessorTarget> {
  public static final MavenAnnotationProcessorTargetType PRODUCTION = new MavenAnnotationProcessorTargetType("maven-annotations-production", false);
  public static final MavenAnnotationProcessorTargetType TESTS = new MavenAnnotationProcessorTargetType("maven-annotations-test", true);

  private final boolean myIsTests;

  public MavenAnnotationProcessorTargetType(String typeId, boolean isTests) {
    super(typeId, false);
    myIsTests = isTests;
  }

  public boolean isTests() {
    return myIsTests;
  }

  @Override
  public @NotNull List<MavenAnnotationProcessorTarget> computeAllTargets(@NotNull JpsModel model) {
    List<MavenAnnotationProcessorTarget> targets = new ArrayList<>();
    for (JpsModule module : model.getProject().getModules()) {
      targets.add(new MavenAnnotationProcessorTarget(this, module));
    }
    return targets;
  }

  @Override
  public @NotNull BuildTargetLoader<MavenAnnotationProcessorTarget> createLoader(@NotNull JpsModel model) {
    return new BuildTargetLoader<MavenAnnotationProcessorTarget>() {

      @Override
      public @Nullable MavenAnnotationProcessorTarget createTarget(@NotNull String targetId) {
        JpsModule module = model.getProject().findModuleByName(targetId);
        return module == null ? null : new MavenAnnotationProcessorTarget(MavenAnnotationProcessorTargetType.this, module);
      }
    };
  }
}
