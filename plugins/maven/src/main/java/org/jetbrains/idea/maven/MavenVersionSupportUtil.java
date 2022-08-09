// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.server.MavenDistribution;

public class MavenVersionSupportUtil {
  public static @Nullable MavenVersionAwareSupportExtension getExtensionFor(MavenDistribution distribution) {
    return MavenVersionAwareSupportExtension.MAVEN_VERSION_SUPPORT.findFirstSafe(
      e -> e.isSupportedByExtension(distribution.getMavenHome().toFile()));
  }
}
