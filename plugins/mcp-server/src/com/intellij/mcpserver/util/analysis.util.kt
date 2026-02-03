package com.intellij.mcpserver.util

import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiDocumentManager
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Awaits for the VFS, uncommited documents and indexing to finish.
 */
suspend fun awaitExternalChangesAndIndexing(project: Project) {
  val dumbService = DumbService.getInstance(project)
  val localFileSystem = LocalFileSystem.getInstance()
  // Get project roots
  val projectDirectory = project.projectDirectory
  val contentRoots = ProjectRootManager.getInstance(project).contentRoots

  val projectDirVirtualFile = localFileSystem.refreshAndFindFileByNioFile(projectDirectory)

  suspendCancellableCoroutine { cont ->
    val toMarkDirty = contentRoots.toMutableList()
    if (projectDirVirtualFile != null) {
      toMarkDirty.add(projectDirVirtualFile)
    }
    VfsUtil.markDirty(true, true, *toMarkDirty.toTypedArray())
    LocalFileSystem.getInstance().refreshFiles(toMarkDirty, true, true) {
      cont.resume(Unit)
    }
  }

  edtWriteAction {
    PsiDocumentManager.getInstance(project).commitAllDocuments()
  }
  suspendCancellableCoroutine { cont ->
    dumbService.runWhenSmart {
      cont.resume(Unit)
    }
  }
}