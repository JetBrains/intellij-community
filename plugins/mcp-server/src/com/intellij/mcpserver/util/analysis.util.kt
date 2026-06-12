package com.intellij.mcpserver.util

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.BaseProjectDirectories.Companion.getBaseDirectories
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.impl.local.LocalFileSystemImpl
import com.intellij.openapi.vfs.newvfs.impl.VirtualFileSystemEntry
import com.intellij.psi.PsiDocumentManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Awaits for the VFS, uncommited documents, and indexing to finish.
 */
suspend fun awaitExternalChangesAndIndexing(project: Project) {
  val dumbService = project.serviceAsync<DumbService>()
  val localFileSystem = LocalFileSystem.getInstance()
  // Get project roots
  val baseDirectories = project.getBaseDirectories()
  (LocalFileSystem.getInstance() as LocalFileSystemImpl).markSuspiciousFilesDirty(emptyList<VirtualFile>())
  val dirtyFiles = baseDirectories.filter { (it as VirtualFileSystemEntry).isDirty }

  if (dirtyFiles.isNotEmpty()) {
    suspendCancellableCoroutine { cont ->
      LocalFileSystem.getInstance().refreshFiles(dirtyFiles, true, true) {
        cont.resume(Unit)
      }
    }
  }

  val psiDocumentManager = project.serviceAsync<PsiDocumentManager>()
  withContext(Dispatchers.EDT) {
    ApplicationManager.getApplication().runWriteAction {
      psiDocumentManager.commitAllDocuments()
    }
  }
  suspendCancellableCoroutine { cont ->
    dumbService.runWhenSmart {
      cont.resume(Unit)
    }
  }
}

suspend fun isProjectIndexing(project: Project): Boolean {
  return project.serviceAsync<DumbService>().isDumb
}

const val INDEXING_PARTIAL_RESULT_REASON: String =
  "Project indexing was in progress; results may be incomplete — retry once indexing completes."

data class IndexingCheckResult<T>(val result: T, val partialResultReason: String?)

suspend fun <T> checkIndexingInProgress(project: Project, block: suspend () -> T): IndexingCheckResult<T> {
  val wasIndexingBefore = isProjectIndexing(project)
  val result = block()
  val isIndexingNow = isProjectIndexing(project)
  val reason = if (wasIndexingBefore || isIndexingNow) INDEXING_PARTIAL_RESULT_REASON else null
  return IndexingCheckResult(result, reason)
}