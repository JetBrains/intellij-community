package com.intellij.searchEverywhereLucene.backend.providers.files

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity



internal class InitLuceneIndex : ProjectActivity {
  override suspend fun execute(project: Project) {
    // Wait until the config is loaded, and we can expect `ProjectFileIndex.getInstance()` to return the files to index.
    val luceneIndex = FileIndex.getInstance(project)
    FileIndex.LOG.info("Scheduling Indexing of all files")
    luceneIndex.scheduleIndexingOp(LuceneFileIndexOperation.IndexAll)
  }
}