// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.project;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.MavenModel;

import java.nio.file.Path;

public interface MavenProjectModelReadHelper {
  MavenModel interpolate(@NotNull Path basedir, VirtualFile file, @NotNull MavenModel model);

  MavenModel assembleInheritance(@NotNull Path projectPomDir, @Nullable MavenModel parent, @NotNull MavenModel model);
}
