// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.indices;


import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.model.MavenArtifactInfo;
import org.jetbrains.idea.maven.onlinecompletion.model.MavenDependencyCompletionItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


public class MavenArtifactSearchResult {
  @Deprecated
  /* @deprecated use getSearchResults instead */
  public List<MavenArtifactInfo> versions;

  private List<MavenDependencyCompletionItem> myResults;


  @Deprecated
  public MavenArtifactSearchResult() {
    this(new ArrayList<>());
  }
  public MavenArtifactSearchResult(@NotNull List<MavenDependencyCompletionItem> results) {
    setVersions(results);
    this.myResults = results;
  }

  public void setResults(List<MavenDependencyCompletionItem> results) {
    setVersions(results);
    myResults = results;
  }

  private void setVersions(@NotNull List<MavenDependencyCompletionItem> results) {
    versions = ContainerUtil.map(results, d -> new MavenArtifactInfo(d, d.getPackaging(), d.getClassifier()));
  }

  public List<MavenDependencyCompletionItem> getSearchResults(){
    return myResults;
  }
}
