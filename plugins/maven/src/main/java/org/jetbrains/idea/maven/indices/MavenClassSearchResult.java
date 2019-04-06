// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.indices;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.onlinecompletion.model.MavenDependencyCompletionItem;

import java.util.List;

public class MavenClassSearchResult extends MavenArtifactSearchResult {
  @Deprecated
  /* @deprecated use getClassName */
  public String className;
  @Deprecated
  /* @deprecated  use getPackageName */
  public String packageName;

  public MavenClassSearchResult(@NotNull List<MavenDependencyCompletionItem> results, String className, String packageName) {
    super(results);
    this.className = className;
    this.packageName = packageName;
  }

  public String getClassName() {
    return className;
  }

  public String getPackageName() {
    return packageName;
  }
}