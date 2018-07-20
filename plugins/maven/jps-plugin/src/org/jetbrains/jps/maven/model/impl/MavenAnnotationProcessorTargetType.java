// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.maven.model.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.builders.BuildTargetLoader;
import org.jetbrains.jps.builders.ModuleBasedBuildTargetType;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.module.JpsModule;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

/**
 * @author ibessonov
 */
public class MavenAnnotationProcessorTargetType extends ModuleBasedBuildTargetType<MavenAnnotationProcessorTarget> {

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

  @NotNull
  @Override
  public List<MavenAnnotationProcessorTarget> computeAllTargets(@NotNull JpsModel model) {
    List<MavenAnnotationProcessorTarget> targets = new ArrayList<>();
    for (JpsModule module : model.getProject().getModules()) {
      targets.add(new MavenAnnotationProcessorTarget(this, module));
    }
    return targets;
  }

  @NotNull
  @Override
  public BuildTargetLoader<MavenAnnotationProcessorTarget> createLoader(@NotNull JpsModel model) {
    Map<String, JpsModule> modules = model.getProject().getModules().stream().collect(toMap(JpsModule::getName, identity()));
    return new BuildTargetLoader<MavenAnnotationProcessorTarget>() {

      @Nullable
      @Override
      public MavenAnnotationProcessorTarget createTarget(@NotNull String targetId) {
        JpsModule module = modules.get(targetId);
        return module == null ? null : new MavenAnnotationProcessorTarget(MavenAnnotationProcessorTargetType.this, module);
      }
    };
  }
}
