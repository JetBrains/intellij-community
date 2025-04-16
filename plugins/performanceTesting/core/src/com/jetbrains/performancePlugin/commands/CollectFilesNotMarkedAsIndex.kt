// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.performancePlugin.commands

import com.intellij.find.ngrams.TrigramIndex
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectCoreUtil
import com.intellij.openapi.roots.ContentIterator
import com.intellij.openapi.ui.playback.PlaybackContext
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.openapi.vfs.newvfs.ManagingFS
import com.intellij.openapi.vfs.newvfs.impl.VirtualFileSystemEntry
import com.intellij.psi.impl.cache.impl.id.IdIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubUpdatingIndex
import com.intellij.util.ExceptionUtil
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileBasedIndexImpl
import com.intellij.util.indexing.IndexingFlag
import com.intellij.util.indexing.IndexingStamp
import com.intellij.util.indexing.dependencies.ProjectIndexingDependenciesService
import com.intellij.util.indexing.roots.IndexableFilesIterator
import com.intellij.util.indexing.roots.kind.SdkOrigin
import org.jetbrains.annotations.NonNls
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.function.Consumer
import java.util.function.Predicate
import kotlin.io.path.Path
import kotlin.io.path.writeText

@Suppress("TestOnlyProblems")
class CollectFilesNotMarkedAsIndex(text: String, line: Int) : PerformanceCommandCoroutineAdapter(text, line) {

  companion object {
    const val NAME: @NonNls String = "collectFilesNotMarkedAsIndex"
    const val PREFIX: @NonNls String = "$CMD_PREFIX$NAME"
  }

  override suspend fun doExecute(context: PlaybackContext) {
    val message = collectFilesNotMarkedAsIndexed(context.project, Path(System.getProperty("integration.tests.indexing.full.problem.file")))
    Path(System.getProperty("integration.tests.indexing.problem.file")).writeText(message)
  }

  override fun getName(): String {
    return NAME
  }

  private fun collectFilesNotMarkedAsIndexed(project: Project, fullLogPath: Path): String {
    val sb = StringBuilder()

    val fbi = FileBasedIndex.getInstance() as FileBasedIndexImpl

    // triple check, actually only one is required to ensure all files is indexed,
    // but we hope that in future we will deprecate some of them
    ReadAction.run<RuntimeException> {
      arrayOf(StubUpdatingIndex.INDEX_ID, TrigramIndex.INDEX_ID, IdIndex.NAME).forEach {
        fbi.ensureUpToDate(it, project, GlobalSearchScope.allScope(project))
      }
    }

    Files.newBufferedWriter(fullLogPath).use { writer ->
      val indexingRequest = project.service<ProjectIndexingDependenciesService>().getReadOnlyTokenForTest()
      val iterator = object : ContentIterator {
        var number = 0

        private fun logIndexingIssue(text: String) {
          if (number < 100) {
            sb.append(text)
            number++
          }
          writer.write(text)
        }

        override fun processFile(fileOrDir: VirtualFile): Boolean {
          if (!fileOrDir.isValid) {
            return true
          }
          if (fileOrDir !is VirtualFileSystemEntry) {
            logIndexingIssue("$fileOrDir (${fileOrDir.javaClass}) is not a VirtualFileSystemEntry\n")
            return true
          }

          checkIndexed(fileOrDir, false, "has no indexing timestamp") {
            val indexedFlagSetOrDisabled = IndexingFlag.isIndexedFlagDisabled() || IndexingFlag.isFileIndexed(it, indexingRequest.getFileIndexingStamp(fileOrDir))
            val hasIndexingStamp = IndexingStamp.hasIndexingTimeStamp(it.id)

            // TODO-ank: should be (indexedFlagSetOrDisabled && hasIndexingStamp) || ProjectCoreUtil.isProjectOrWorkspaceFile(it, it.fileType)
            //  Currently not true, because STUB indexes invalidate file stamp when writing debug info, Subindexers via subindexing stamp, etc.
            //  See com.intellij.integration.kotlingPlugin.tests.highlight.AcceptanceMppProjectIntegrationTest for reproducer
            hasIndexingStamp ||
            // com.intellij.util.indexing.FileBasedIndexImpl.getAffectedIndexCandidates returns an empty list for such files
            ProjectCoreUtil.isProjectOrWorkspaceFile(it, it.fileType)
          }
          return true
        }

        private fun checkIndexed(fileOrDir: VirtualFileSystemEntry,
                                 checkDirectories: Boolean,
                                 errorMessagePart: String,
                                 isIndexed: Predicate<VirtualFileSystemEntry>) {
          if (!checkDirectories && fileOrDir.isDirectory) return
          if (!isIndexed.test(fileOrDir)) {
            if (fileOrDir.isDirectory) {
              logIndexingIssue("Directory $fileOrDir $errorMessagePart\n")
              return
            }
            if (fbi.isTooLarge(fileOrDir)) {
              return
            }
            try {
              fileOrDir.contentsToByteArray()
              if (fbi.filesToUpdateCollector.containsFileId(fileOrDir.id)) {
                logIndexingIssue("$fileOrDir (id=${fileOrDir.id}) $errorMessagePart because is changed\n")
              }
              else {
                logIndexingIssue("$fileOrDir (id=${fileOrDir.id}) $errorMessagePart\n")
              }
            }
            catch (e: IOException) {
              //we ignore such files
            }
          }
        }
      }

      val originalOrderedProviders: List<IndexableFilesIterator> = fbi.getIndexableFilesProviders(project)

      val orderedProviders: MutableList<IndexableFilesIterator> = ArrayList()
      originalOrderedProviders
        .filter { p: IndexableFilesIterator -> p.origin !is SdkOrigin }
        .toCollection(orderedProviders)

      originalOrderedProviders
        .filter { p: IndexableFilesIterator -> p.origin is SdkOrigin }
        .toCollection(orderedProviders )

      for (provider in orderedProviders) {
        provider.iterateFiles(project, iterator, VirtualFileFilter.ALL)
      }

      val textConsumer = Consumer<String> { text ->
        sb.append(text)
        writer.write(text)
      }

      val lockedFiles = IndexingFlag.dumpLockedFiles()
      if (!lockedFiles.isEmpty()) {
        textConsumer.accept("Locked files' ids in IndexingFlag: ${lockedFiles.contentToString()}\n")
        dumpIdsWithPaths(lockedFiles, textConsumer)
      }

      try {
        val cachedUnfinishedFiles = IndexingStamp.dumpCachedUnfinishedFiles()
        if (!cachedUnfinishedFiles.isEmpty()) {
          textConsumer.accept("Unfinished cached files' ids in IndexingStamp: ${cachedUnfinishedFiles.contentToString()}\n")
          dumpIdsWithPaths(cachedUnfinishedFiles, textConsumer)
          textConsumer.accept("All changed unindexed files: ${fbi.allFilesToUpdate}")
        }
        return@use
      }
      catch (e: RuntimeException) {
        textConsumer.accept("Exception while checking IndexingStamp cache:\n")
        textConsumer.accept(ExceptionUtil.getThrowableText(e))
        textConsumer.accept("\n")
      }
    }

    return sb.toString()
  }

  private fun dumpIdsWithPaths(ids: IntArray,
                               consumer: Consumer<String>) {
    for (fileId in ids) {
      consumer.accept(fileId.toString())
      fileId.findFile()?.also {
        consumer.accept(" ")
        consumer.accept(it.path)
      }
      consumer.accept("\n")
    }
  }

  private fun Int.findFile(): VirtualFile? = ManagingFS.getInstance().findFileById(this)

}