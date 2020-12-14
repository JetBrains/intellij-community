// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.server.wsl;

import com.intellij.execution.Executor;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.wsl.WSLDistribution;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.server.AbstractMavenServerRemoteProcessSupport;
import org.jetbrains.idea.maven.server.MavenDistribution;

public class WslMavenServerRemoteProcessSupport extends AbstractMavenServerRemoteProcessSupport {
  private final WSLDistribution myWslDistribution;

  public WslMavenServerRemoteProcessSupport(@NotNull WSLDistribution wslDistribution,
                                            @NotNull Sdk jdk,
                                            @Nullable String vmOptions,
                                            @Nullable MavenDistribution mavenDistribution,
                                            @NotNull Project project,
                                            @Nullable Integer debugPort) {
    super(jdk, vmOptions, mavenDistribution, project, debugPort);
    myWslDistribution = wslDistribution;
  }

  protected RunProfileState getRunProfileState(@NotNull Object target, @NotNull Object configuration, @NotNull Executor executor) {
    return new WslMavenCmdState(myWslDistribution, myJdk, myOptions, myDistribution, myProject, myDebugPort);
  }


}
