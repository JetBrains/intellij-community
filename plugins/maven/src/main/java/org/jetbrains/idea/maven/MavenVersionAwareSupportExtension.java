// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.roots.ui.distribution.DistributionInfo;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.server.MavenDistribution;

import java.io.File;
import java.util.Collections;
import java.util.List;

@ApiStatus.Internal
public interface MavenVersionAwareSupportExtension {
  ExtensionPointName<MavenVersionAwareSupportExtension> MAVEN_VERSION_SUPPORT
    = new ExtensionPointName<>("org.jetbrains.idea.maven.versionAwareMavenSupport");

  boolean isSupportedByExtension(@Nullable File mavenHome);

  @Nullable File getMavenHomeFile(@Nullable String mavenHomeName);

  @Nullable String asMavenHome(DistributionInfo distribution);

  @Nullable DistributionInfo asDistributionInfo(String mavenHome);

  @NotNull List<File> collectClassPathAndLibsFolder(@NotNull MavenDistribution distribution);

  @NotNull
  default List<String> supportedBundles() {
    return Collections.emptyList();
  }
}
