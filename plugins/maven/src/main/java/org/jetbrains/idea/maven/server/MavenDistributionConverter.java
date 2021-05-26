// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.server;

import com.intellij.execution.wsl.WslPath;
import com.intellij.util.xmlb.Converter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public class MavenDistributionConverter extends Converter<MavenDistribution> {
  @Nullable
  @Override
  public MavenDistribution fromString(@NotNull String value) {
    File file = MavenServerManager.getMavenHomeFile(value);
    if (file == null) return null;
    WslPath wslPath = WslPath.parseWindowsUncPath(file.getAbsolutePath());
    if (wslPath == null) {
      return new LocalMavenDistribution(file, value);
    }
    else {
      return new WslMavenDistribution(wslPath.getDistribution(), wslPath.getLinuxPath(), value);
    }
  }

  @Override
  public @Nullable String toString(@NotNull MavenDistribution value) {
    return value.getMavenHome().getAbsolutePath();
  }
}
