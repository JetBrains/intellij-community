// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.impl.huggingFace.service

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.psi.PsiManager
import com.intellij.python.community.impl.huggingFace.HuggingFaceUtil
import com.intellij.python.community.impl.huggingFace.cache.HuggingFaceCacheUpdateListener
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.messages.MessageBusConnection
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyImportElement
import org.jetbrains.annotations.ApiStatus



@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class HuggingFacePluginManager(val project: Project) : Disposable {
  // todo: separation of concerns, split into:
  // - LibraryImportChecker
  // - CacheManager
  // - ReferenceRefresher
  private var libraryImportStatus: LibraryImportStatus = LibraryImportStatus.NOT_CHECKED
  private var cacheTimestamp: Long = 0
  private val connection: MessageBusConnection = project.messageBus.connect(this)
  private var changesCount: Int = 0
  private var documentListener: DocumentListener? = null

  init {
    setupListeners()

    connection.subscribe(HuggingFaceCacheUpdateListener.TOPIC, object : HuggingFaceCacheUpdateListener {
      override fun cacheUpdated() {
        refreshReferencesInProject(project)
      }
    })
  }

  private fun setupListeners() {
    connection.subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
      override fun after(events: List<VFileEvent>) {
        if (events.any { it.file?.extension in listOf("py", "ipynb") }) {
          changesCount++
          if (changesCount >= CHANGES_NUM_THRESHOLD) {
            checkLibraryImportStatus()
          }
        }
      }
    })

    documentListener = object : DocumentListener {
      override fun documentChanged(event: DocumentEvent) {
        if (event.newFragment.toString().contains("import")) {
          checkLibraryImportStatus()
        }
      }
    }.also { listener ->
      EditorFactory.getInstance().eventMulticaster.addDocumentListener(listener, connection)
    }
  }

  @RequiresBackgroundThread
  fun isLibraryImported(): Boolean {
    if (libraryImportStatus == LibraryImportStatus.NOT_CHECKED) {
      checkLibraryImportStatus()
    }
    return libraryImportStatus == LibraryImportStatus.IMPORTED
  }

  private fun checkLibraryImportStatus() {
    if (libraryImportStatus == LibraryImportStatus.IMPORTED) return

    // Check if the status is NOT_CHECKED or enough time has passed since the last check
    if (libraryImportStatus == LibraryImportStatus.NOT_CHECKED || System.currentTimeMillis() - cacheTimestamp > INVALIDATION_THRESHOLD_MS)
    {
      val isImported = isAnyHFLibraryImportedInProject(project)

      libraryImportStatus = if (isImported) {
        HuggingFaceUtil.triggerCacheFillIfNeeded(project)
        connection.disconnect()
        detachDocumentListener()
        LibraryImportStatus.IMPORTED
      } else {
        LibraryImportStatus.NOT_IMPORTED
      }
      cacheTimestamp = System.currentTimeMillis()
    }
  }

  private enum class LibraryImportStatus {
    NOT_CHECKED, IMPORTED, NOT_IMPORTED
  }

  private fun detachDocumentListener() {
    documentListener?.let { listener ->
      EditorFactory.getInstance().eventMulticaster.removeDocumentListener(listener)
      documentListener = null // Prevent further removal attempts
    }
  }

  override fun dispose() {
    connection.disconnect()
    detachDocumentListener()
  }

  private fun refreshReferencesInProject(project: Project) {
    DaemonCodeAnalyzer.getInstance(project).restart()
  }

  fun isAnyHFLibraryImportedInProject(project: Project): Boolean {
    var isLibraryImported = false

    ProjectFileIndex.getInstance(project).iterateContent { virtualFile ->
      if (virtualFile.extension in listOf("py", "ipynb")) {
        val pythonFile = PsiManager.getInstance(project).findFile(virtualFile)
        if (pythonFile is PyFile) {
          isLibraryImported = isLibraryImported or isAnyHFLibraryImportedInFile(pythonFile)
        }
      }
      !isLibraryImported
    }

    return isLibraryImported
  }

  private fun isAnyHFLibraryImportedInFile(file: PyFile): Boolean {
    val isDirectlyImported = file.importTargets.any { importStmt ->
      huggingFaceRelevantLibraries.any { lib -> importStmt.importedQName.toString().contains(lib) }
    }

    val isFromImported = file.fromImports.any { fromImport ->
      huggingFaceRelevantLibraries.any { lib -> fromImport.importSourceQName?.toString()?.contains(lib) == true }
    }

    val isQualifiedImported: Boolean = file.importTargets.any { importStmt: PyImportElement? ->
      huggingFaceRelevantLibraries.any { lib: String -> importStmt?.importedQName?.components?.contains(lib) == true }
    }
    return isDirectlyImported || isFromImported || isQualifiedImported
  }

  companion object {
    private const val INVALIDATION_THRESHOLD_MS = 5 * 60 * 1000
    private const val CHANGES_NUM_THRESHOLD = 10

    private val huggingFaceRelevantLibraries = setOf(
      "diffusers", "transformers", "allennlp", "spacy",
      "asteroid", "flair", "keras", "sentence-transformers",
      "stable-baselines3", "adapters", "huggingface_hub",
    )
  }
}
