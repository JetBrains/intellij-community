// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.maven.model.impl;

import com.dynatrace.hash4j.hashing.HashSink;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene Zhuravlev
 */
@Tag("resource")
@ApiStatus.Internal
public final class ResourceRootConfiguration extends FilePattern {
  @Tag("directory") public @NotNull String directory;

  @Tag("targetPath") public @Nullable String targetPath;

  @Attribute("filtered")
  public boolean isFiltered;

  public void computeConfigurationHash(@NotNull HashSink hash) {
    hash.putString(directory);
    if (targetPath == null) {
      hash.putInt(-1);
    }
    else {
      hash.putString(targetPath);
    }
    hash.putBoolean(isFiltered);
  }

  @Override
  public String toString() {
    return "ResourceRootConfiguration{" +
           "directory='" + directory + '\'' +
           ", targetPath='" + targetPath + '\'' +
           ", isFiltered=" + isFiltered +
           ", includes=" + includes +
           ", excludes=" + excludes +
           '}';
  }
}
