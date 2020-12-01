// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.execution.target;

import com.intellij.execution.target.TargetEnvironmentConfiguration;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface MavenRuntimeUIFactory {

  @Nullable
  Configurable createConfigurable(@NotNull Project project,
                                  @NotNull MavenRuntimeTargetConfiguration mavenRuntimeTargetConfiguration,
                                  @NotNull TargetEnvironmentConfiguration targetEnvironmentConfiguration);
}
