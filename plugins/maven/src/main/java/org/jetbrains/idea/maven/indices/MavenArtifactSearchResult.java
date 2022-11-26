// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.indices;


import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.onlinecompletion.model.MavenRepositoryArtifactInfo;


public class MavenArtifactSearchResult {
  private MavenRepositoryArtifactInfo myInfo;


  public MavenArtifactSearchResult(@NotNull MavenRepositoryArtifactInfo info) {
    this.myInfo = info;
  }

  public MavenRepositoryArtifactInfo getSearchResults(){
    return myInfo;
  }
}
