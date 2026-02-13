package com.intellij.selucene.backend.providers.files

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.platform.backend.observation.Observation


internal class InitLuceneIndex : ProjectActivity {
  override suspend fun execute(project: Project) {
    // Wait until config is loaded and we can expect `ProjectFileIndex.getInstance()` to return the files to index.
    Observation.awaitConfiguration(project)
    val luceneIndex = FileIndex.getInstance(project)
    LOG.info("Scheduling Indexing of all files")
    luceneIndex.scheduleIndexingOp(LuceneFileIndexOperation.IndexAll)
  }

  companion object {
    val LOG: Logger = logger<InitLuceneIndex>()
  }
}