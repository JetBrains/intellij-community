// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.server;

import com.intellij.util.xmlb.Converter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public class MavenDistributionConverter extends Converter<MavenDistribution> {
  @Nullable
  @Override
  public MavenDistribution fromString(@NotNull String value) {
    File file = MavenServerManager.getMavenHomeFile(value);
    return file == null ? null : new MavenDistribution(file, value);
  }

  @Override
  public @Nullable String toString(@NotNull MavenDistribution value) {
    return value.getMavenHome().getAbsolutePath();
  }
}
