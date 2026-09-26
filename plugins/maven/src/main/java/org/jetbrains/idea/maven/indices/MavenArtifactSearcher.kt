// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.maven.indices

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.idea.maven.completion.MavenDependencySearchService
import org.jetbrains.idea.maven.model.MavenRepoArtifactInfo
import java.util.function.Consumer

class MavenArtifactSearcher : MavenSearcher<MavenArtifactSearchResult>() {
  override fun searchImpl(project: Project, pattern: String, maxResult: Int): List<MavenArtifactSearchResult> {
    if (StringUtil.isEmpty(pattern)) {
      return mutableListOf()
    }
    val searchResults = ArrayList<MavenRepoArtifactInfo>()
    val searchService = MavenDependencySearchService.getInstance(project)
    val useLocalProvidersOnly = ApplicationManager.getApplication().isUnitTestMode()
    runBlockingCancellable {
      searchService.fulltextSearch(pattern, false, useLocalProvidersOnly, Consumer {
        searchResults.add(it)
      })
    }
    return processResults(searchResults)
  }

  companion object {
    private fun processResults(searchResults: List<MavenRepoArtifactInfo>): List<MavenArtifactSearchResult> {
      return searchResults.map { MavenArtifactSearchResult(it) }
    }
  }
}
