// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.indices.searcher

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.runBlockingCancellable
import kotlinx.coroutines.runBlocking
import org.jetbrains.idea.maven.indices.MavenClassSearchResult
import org.jetbrains.idea.maven.indices.MavenClassSearcher
import org.jetbrains.idea.maven.indices.MavenSystemIndicesManager
import org.jetbrains.idea.maven.indices.MavenUpdatableIndex
import org.jetbrains.idea.maven.model.MavenRepositoryInfo

@Service
class MavenLuceneIndexer {
  suspend fun search(patternForQuery: String, repos: List<MavenRepositoryInfo>, maxResult: Int = 50): List<MavenClassSearchResult> {
    val pattern = MavenClassSearcher.preparePattern(patternForQuery);
    val result = repos
      .mapNotNull { MavenSystemIndicesManager.getInstance().getClassIndexForRepository(it) }
      .flatMap { it.search(pattern, maxResult).asSequence() }
      .toSet()
    return MavenClassSearcher.processResults(result, pattern, maxResult).sortedWith(
      Comparator.comparing(MavenClassSearchResult::getClassName))
  }

  fun searchSync(patternForQuery: String, repos: List<MavenRepositoryInfo>, maxResult: Int = 50): List<MavenClassSearchResult> {
    return runBlocking {
      search(patternForQuery, repos, maxResult)
    }

  }

  suspend fun update(repos: List<MavenRepositoryInfo>, explicit: Boolean) {
    val systemIndicesManager = MavenSystemIndicesManager.getInstance()
    val indices = repos
      .map { systemIndicesManager.getClassIndexForRepository(it) }
      .filterIsInstance<MavenUpdatableIndex>()
      .toList()
    systemIndicesManager.scheduleUpdateIndexContent(indices, explicit)

  }

  companion object {
    @JvmStatic
    fun getInstance() = service<MavenLuceneIndexer>()
  }
}