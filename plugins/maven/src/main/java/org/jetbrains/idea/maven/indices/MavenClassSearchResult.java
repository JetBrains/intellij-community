// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  @NlsSafe
  public String getClassName() {
    return className;
  }

  @NlsSafe
  public String getPackageName() {
    return packageName;
  }
}