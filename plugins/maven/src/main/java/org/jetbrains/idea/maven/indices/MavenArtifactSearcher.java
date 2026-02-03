// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.indices;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.WaitFor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.idea.maven.onlinecompletion.model.MavenRepositoryArtifactInfo;
import org.jetbrains.idea.reposearch.DependencySearchService;
import org.jetbrains.idea.reposearch.SearchParameters;

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
    DependencySearchService searchService = DependencySearchService.getInstance(project);
    boolean useLocalProvidersOnly = ApplicationManager.getApplication().isUnitTestMode();
    SearchParameters parameters = new SearchParameters(false, useLocalProvidersOnly);
    Promise<Integer> asyncPromise = searchService.fulltextSearch(pattern, parameters, mdci -> {
      if (mdci instanceof MavenRepositoryArtifactInfo) {
        searchResults.add((MavenRepositoryArtifactInfo)mdci);
      }
    });
    new WaitFor(1000) {
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
