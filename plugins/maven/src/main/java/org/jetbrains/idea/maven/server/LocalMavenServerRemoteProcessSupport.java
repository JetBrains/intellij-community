// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.server;

import com.intellij.execution.Executor;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LocalMavenServerRemoteProcessSupport extends AbstractMavenServerRemoteProcessSupport {
  public LocalMavenServerRemoteProcessSupport(@NotNull Sdk jdk,
                                              @Nullable String vmOptions,
                                              @NotNull MavenDistribution mavenDistribution,
                                              @NotNull Project project,
                                              @Nullable Integer debugPort) {
    super(jdk, vmOptions, mavenDistribution, project, debugPort);
  }

  protected RunProfileState getRunProfileState(@NotNull Object target, @NotNull Object configuration, @NotNull Executor executor) {
    return new MavenServerCMDState(myJdk, myOptions, myDistribution, myDebugPort);
  }

  @Override
  public String type() {
    return "Local";
  }
}
