// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.indices;

import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.onlinecompletion.model.MavenRepositoryArtifactInfo;

public class MavenClassSearchResult extends MavenArtifactSearchResult {
  private final String className;
  private final String packageName;

  public MavenClassSearchResult(@NotNull MavenRepositoryArtifactInfo results, String className, String packageName) {
    super(results);
    this.className = className;
    this.packageName = packageName;
  }

  public @NlsSafe String getClassName() {
    return className;
  }

  public @NlsSafe String getPackageName() {
    return packageName;
  }
}