// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.maven.model.impl;

import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.builders.BuildRootDescriptor;

import java.io.File;
import java.io.FileFilter;

/**
 * @author Eugene Zhuravlev
 */
public final class MavenResourceRootDescriptor extends BuildRootDescriptor {
  private final MavenResourcesTarget myTarget;
  private final ResourceRootConfiguration myConfig;
  private final File myFile;
  private final String myId;
  private final boolean myOverwrite;

  private final int myIndexInPom;

  public MavenResourceRootDescriptor(@NotNull MavenResourcesTarget target,
                                     ResourceRootConfiguration config,
                                     int indexInPom,
                                     boolean overwrite) {
    myTarget = target;
    myConfig = config;
    final String path = FileUtil.toCanonicalPath(config.directory);
    myFile = new File(path);
    myId = path;
    myIndexInPom = indexInPom;
    myOverwrite = overwrite;
  }

  public ResourceRootConfiguration getConfiguration() {
    return myConfig;
  }

  @Override
  public @NotNull String getRootId() {
    return myId;
  }

  @Override
  public @NotNull File getRootFile() {
    return myFile;
  }

  @Override
  public @NotNull MavenResourcesTarget getTarget() {
    return myTarget;
  }

  @Override
  public @NotNull FileFilter createFileFilter() {
    return new MavenResourceFileFilter(myFile, myConfig);
  }

  public int getIndexInPom() {
    return myIndexInPom;
  }

  public boolean isOverwrite() {
    return myOverwrite;
  }
}
