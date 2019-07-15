// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.indices;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.WaitFor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.idea.maven.onlinecompletion.DependencySearchService;
import org.jetbrains.idea.maven.onlinecompletion.model.MavenRepositoryArtifactInfo;
import org.jetbrains.idea.maven.onlinecompletion.model.SearchParameters;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MavenArtifactSearcher extends MavenSearcher<MavenArtifactSearchResult> {


  @Override
  protected List<MavenArtifactSearchResult> searchImpl(Project project, String pattern, int maxResult) {
    if (StringUtil.isEmpty(pattern)) {
      return Collections.emptyList();
    }
    List<MavenRepositoryArtifactInfo> searchResults = new ArrayList<>();
    DependencySearchService searchService = MavenProjectIndicesManager.getInstance(project).getDependencySearchService();
    Promise<Void> asyncPromise = searchService.fulltextSearch(pattern, SearchParameters.DEFAULT, mdci -> searchResults.add(mdci));
    new WaitFor((int)SearchParameters.DEFAULT.getMillisToWait()) {
      @Override
      protected boolean condition() {
        return asyncPromise.getState() != Promise.State.PENDING;
      }
    };
    return processResults(searchResults);
  }

  private static List<MavenArtifactSearchResult> processResults(List<MavenRepositoryArtifactInfo> searchResults) {
    return ContainerUtil.map(searchResults, MavenArtifactSearchResult::new);
  }
}
