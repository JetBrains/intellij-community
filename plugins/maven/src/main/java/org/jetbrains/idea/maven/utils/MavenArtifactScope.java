// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.utils;

import org.jetbrains.annotations.Nullable;

/**
 * @author Konstantin Bulenkov
 */
public enum MavenArtifactScope {
  COMPILE, PROVIDED, RUNTIME, TEST, SYSTEM, IMPORT;

  public static @Nullable MavenArtifactScope fromString(String name) {
    for (MavenArtifactScope scope : MavenArtifactScope.values()) {
      if (scope.name().equalsIgnoreCase(name)) return scope;
    }
    return null;
  }
}
