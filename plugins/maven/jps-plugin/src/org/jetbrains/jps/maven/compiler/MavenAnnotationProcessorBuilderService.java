// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.maven.compiler;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildTargetType;
import org.jetbrains.jps.incremental.BuilderService;
import org.jetbrains.jps.maven.model.impl.MavenAnnotationProcessorTargetType;

import java.util.Arrays;
import java.util.List;

/**
 * @author ibessonov
 */
public class MavenAnnotationProcessorBuilderService extends BuilderService {

  @NotNull
  @Override
  public List<? extends BuildTargetType<?>> getTargetTypes() {
    return Arrays.asList(MavenAnnotationProcessorTargetType.PRODUCTION, MavenAnnotationProcessorTargetType.TESTS);
  }
}
