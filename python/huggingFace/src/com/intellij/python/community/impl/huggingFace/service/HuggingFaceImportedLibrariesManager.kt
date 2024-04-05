// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.community.impl.huggingFace.service

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
import com.intellij.python.community.impl.huggingFace.HuggingFaceRelevantLibraries
import com.intellij.python.community.impl.huggingFace.cache.HuggingFaceCacheFillService
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.messages.MessageBusConnection
import com.jetbrains.python.psi.PyFile
import com.jetbrains.python.psi.PyImportElement
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class HuggingFaceImportedLibrariesManager(val project: Project) : Disposable {
  private var libraryImportStatus: LibraryImportStatus = LibraryImportStatus.NOT_CHECKED
  private var cacheTimestamp: Long = 0
  private val connection: MessageBusConnection = project.messageBus.connect(this)
  private var documentListener: DocumentListener? = null
  private val cacheFillService: HuggingFaceCacheFillService = project.getService(HuggingFaceCacheFillService::class.java)
  private enum class LibraryImportStatus { NOT_CHECKED, IMPORTED, NOT_IMPORTED }

  init { setupListeners() }

  private fun setupListeners() {
    connection.subscribe(VirtualFileManager.VFS_CHANGES, HuggingFaceFileChangesListener { checkLibraryImportStatus() })
    val documentListener = HuggingFaceImportDetectionListener { checkLibraryImportStatus() }
    EditorFactory.getInstance().eventMulticaster.addDocumentListener(documentListener, connection)
    this.documentListener = documentListener
  }

  @RequiresBackgroundThread
  fun isLibraryImported(): Boolean {
    if (libraryImportStatus != LibraryImportStatus.IMPORTED) checkLibraryImportStatus()
    return libraryImportStatus == LibraryImportStatus.IMPORTED
  }

  private fun checkLibraryImportStatus() {
    if (libraryImportStatus == LibraryImportStatus.IMPORTED) return

    val isUpdateTime = System.currentTimeMillis() - cacheTimestamp > HuggingFaceLibrariesManagerConfig.INVALIDATION_THRESHOLD_MS
    if (libraryImportStatus == LibraryImportStatus.NOT_CHECKED || isUpdateTime)
    {
      val isImported = isAnyHFLibraryImportedInProject(project)

      libraryImportStatus = if (isImported) {
        cacheFillService.triggerCacheFillIfNeeded()
        detachListeners()
        LibraryImportStatus.IMPORTED
      } else {
        LibraryImportStatus.NOT_IMPORTED
      }
      cacheTimestamp = System.currentTimeMillis()
    }
  }

  private fun isAnyHFLibraryImportedInProject(project: Project): Boolean {
    var isLibraryImported = false

    ProjectFileIndex.getInstance(project).iterateContent { virtualFile ->
      if (virtualFile.extension in listOf("py", "ipynb")) {
        val pythonFile = PsiManager.getInstance(project).findFile(virtualFile)
        if (pythonFile is PyFile) isLibraryImported = isLibraryImported or isAnyHFLibraryImportedInFile(pythonFile)
      }
      !isLibraryImported
    }

    return isLibraryImported
  }

  private fun isAnyHFLibraryImportedInFile(file: PyFile): Boolean {
    val isDirectlyImported = file.importTargets.any { importStmt ->
      HuggingFaceRelevantLibraries.relevantLibraries.any { lib -> importStmt.importedQName.toString().contains(lib) }
    }

    val isFromImported = file.fromImports.any { fromImport ->
      HuggingFaceRelevantLibraries.relevantLibraries.any { lib -> fromImport.importSourceQName?.toString()?.contains(lib) == true }
    }

    val isQualifiedImported: Boolean = file.importTargets.any { importStmt: PyImportElement? ->
      HuggingFaceRelevantLibraries.relevantLibraries.any { lib: String -> importStmt?.importedQName?.components?.contains(lib) == true }
    }
    return isDirectlyImported || isFromImported || isQualifiedImported
  }

  private fun detachListeners() {
    documentListener?.let { listener ->
      EditorFactory.getInstance().eventMulticaster.removeDocumentListener(listener)
      documentListener = null
    }
    connection.disconnect()
  }

  override fun dispose() {
    detachListeners()
  }
}

private class HuggingFaceFileChangesListener(private val onThresholdReached: () -> Unit) : BulkFileListener {
  private var fileChangesCounter = 0

  override fun after(events: List<VFileEvent>) {
    if (events.any { it.file?.extension in listOf("py", "ipynb") }) {
      fileChangesCounter++
      if (fileChangesCounter >= HuggingFaceLibrariesManagerConfig.CHANGES_NUM_THRESHOLD) onThresholdReached()
    }
  }
}

private class HuggingFaceImportDetectionListener(private val onImportDetected: () -> Unit) : DocumentListener {
  override fun documentChanged(event: DocumentEvent) {
    if (event.newFragment.toString().contains("import")) {
      onImportDetected()
    }
  }
}

private object HuggingFaceLibrariesManagerConfig {
  const val CHANGES_NUM_THRESHOLD = 10
  const val INVALIDATION_THRESHOLD_MS = 5 * 60 * 1000
}
