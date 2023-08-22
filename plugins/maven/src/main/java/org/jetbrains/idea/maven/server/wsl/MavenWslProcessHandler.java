// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.server.wsl;

import com.intellij.execution.process.KillableColoredProcessHandler;
import com.intellij.execution.wsl.WSLDistribution;
import org.jetbrains.annotations.NotNull;

public class MavenWslProcessHandler extends KillableColoredProcessHandler {
  private final WSLDistribution myDistribution;

  public MavenWslProcessHandler(@NotNull Process process,
                                String commandLine,
                                WSLDistribution distribution) {
    super(process, commandLine);
    myDistribution = distribution;
  }

  @Override
  public boolean shouldKillProcessSoftly() {
    return true;
  }
}
