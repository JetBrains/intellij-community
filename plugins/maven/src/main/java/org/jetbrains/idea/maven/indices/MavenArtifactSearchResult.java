// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.indices;


import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.model.MavenArtifactInfo;
import org.jetbrains.idea.maven.onlinecompletion.model.MavenDependencyCompletionItem;
import org.jetbrains.idea.maven.onlinecompletion.model.MavenRepositoryArtifactInfo;

import java.util.List;


public class MavenArtifactSearchResult {
  /**
   *  @deprecated use getSearchResults instead
   */
  @Deprecated
  public List<MavenArtifactInfo> versions;

  private  MavenRepositoryArtifactInfo myInfo;


  /**
   * @deprecated use {@link #MavenArtifactSearchResult(MavenRepositoryArtifactInfo)}
   */
  @Deprecated
  public MavenArtifactSearchResult() {
    this(new MavenRepositoryArtifactInfo("", "", new MavenDependencyCompletionItem[0]));
  }

  public MavenArtifactSearchResult(@NotNull MavenRepositoryArtifactInfo info) {
    setVersions(info);
    this.myInfo = info;
  }

  private void setVersions(@NotNull MavenRepositoryArtifactInfo info) {
    versions = ContainerUtil.map(info.getItems(), d -> new MavenArtifactInfo(d, d.getPackaging(), d.getClassifier()));
  }

  public MavenRepositoryArtifactInfo getSearchResults(){
    return myInfo;
  }
}
