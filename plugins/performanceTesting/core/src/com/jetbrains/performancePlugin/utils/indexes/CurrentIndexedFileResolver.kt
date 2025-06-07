// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.utils.indexes

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileBasedIndexEx
import com.intellij.util.indexing.roots.IndexableFilesDeduplicateFilter
import com.intellij.util.indexing.roots.IndexableFilesIterator
import com.jetbrains.performancePlugin.PerformanceTestingBundle
import com.jetbrains.performancePlugin.utils.indexes.CurrentIndexedFileResolver.getAllToBeIndexedFilesInProject

/**
 * Provides access to indexed files that belong to the current project:
 * - [getAllToBeIndexedFilesInProject]
 */
object CurrentIndexedFileResolver {

  fun getAllToBeIndexedFilesInProject(project: Project, indicator: ProgressIndicator): Map<IndexableFilesIterator, Set<VirtualFile>> {
    indicator.text = PerformanceTestingBundle.message("checking.shared.indexes.collecting.all.indexed.files")
    indicator.isIndeterminate = false
    indicator.fraction = 0.0

    val providerToFiles = hashMapOf<IndexableFilesIterator, Set<VirtualFile>>()
    val providers = (FileBasedIndex.getInstance() as FileBasedIndexEx).getIndexableFilesProviders(project)
    val indexableFilesDeduplicateFilter = IndexableFilesDeduplicateFilter.create()
    for ((finished, provider) in providers.withIndex()) {
      indicator.checkCanceled()
      val providerFiles = hashSetOf<VirtualFile>()
      if (!provider.iterateFiles(project, { fileOrDir ->
          if (!fileOrDir.isDirectory) {
            providerFiles += fileOrDir
          }
          true
        }, indexableFilesDeduplicateFilter)) {
        break
      }
      indicator.fraction = (finished + 1) * 1.0 / providers.size
      providerToFiles[provider] = providerFiles
    }
    return providerToFiles
  }
}