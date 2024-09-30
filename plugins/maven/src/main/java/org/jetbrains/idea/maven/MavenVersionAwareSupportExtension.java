// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.project.StaticResolvedMavenHomeType;
import org.jetbrains.idea.maven.server.MavenDistribution;

import java.io.File;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

@ApiStatus.Internal
public interface MavenVersionAwareSupportExtension {
  @NonNls String DEFAULT_MAIN_CLASS = "org.jetbrains.idea.maven.server.RemoteMavenServer";

  ExtensionPointName<MavenVersionAwareSupportExtension> MAVEN_VERSION_SUPPORT
    = new ExtensionPointName<>("org.jetbrains.idea.maven.versionAwareMavenSupport");

  boolean isSupportedByExtension(@Nullable File mavenHome);

  @Nullable
  Path getMavenHomeFile(@Nullable StaticResolvedMavenHomeType mavenHomeType);

  @NotNull
  List<File> collectClassPathAndLibsFolder(@NotNull MavenDistribution distribution);

  @NotNull
  default List<@NonNls String> getAdditionalVmParameters() {
    return Collections.emptyList();
  }

  default String getMainClass(MavenDistribution distribution) {
    return DEFAULT_MAIN_CLASS;
  }
}
