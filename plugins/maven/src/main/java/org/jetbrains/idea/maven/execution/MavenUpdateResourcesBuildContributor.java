// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.execution;

import com.intellij.compiler.impl.UpdateResourcesBuildContributor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.ModuleBasedBuildTargetType;
import org.jetbrains.jps.maven.model.impl.MavenResourcesTargetType;

import java.util.Arrays;
import java.util.List;

public final class MavenUpdateResourcesBuildContributor implements UpdateResourcesBuildContributor {
  @Override
  public @NotNull List<? extends ModuleBasedBuildTargetType<?>> getResourceTargetTypes() {
    return Arrays.asList(MavenResourcesTargetType.PRODUCTION, MavenResourcesTargetType.TEST);
  }
}
