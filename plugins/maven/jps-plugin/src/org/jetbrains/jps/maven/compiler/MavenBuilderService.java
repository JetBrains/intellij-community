// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.maven.compiler;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildTargetType;
import org.jetbrains.jps.incremental.BuilderService;
import org.jetbrains.jps.incremental.ModuleLevelBuilder;
import org.jetbrains.jps.incremental.TargetBuilder;
import org.jetbrains.jps.maven.model.impl.MavenAnnotationProcessorTargetType;
import org.jetbrains.jps.maven.model.impl.MavenResourcesTargetType;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 */
public class MavenBuilderService extends BuilderService {
  @Override
  public @NotNull List<? extends BuildTargetType<?>> getTargetTypes() {
    return Arrays.asList(MavenResourcesTargetType.PRODUCTION, MavenResourcesTargetType.TEST,
                         MavenAnnotationProcessorTargetType.PRODUCTION, MavenAnnotationProcessorTargetType.TESTS
    );
  }

  @Override
  public @NotNull List<? extends ModuleLevelBuilder> createModuleLevelBuilders() {
    return List.of(new MavenFilteredJarModuleBuilder());
  }

  @Override
  public @NotNull List<? extends TargetBuilder<?, ?>> createBuilders() {
    return Collections.singletonList(new MavenResourcesBuilder());
  }
}
