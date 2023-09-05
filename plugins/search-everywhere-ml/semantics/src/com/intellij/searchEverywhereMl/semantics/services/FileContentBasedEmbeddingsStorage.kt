package com.intellij.searchEverywhereMl.semantics.services

import com.intellij.openapi.application.readAction
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.isFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.searchEverywhereMl.semantics.indices.IndexableEntity

abstract class FileContentBasedEmbeddingsStorage<T : IndexableEntity>(project: Project) : DiskSynchronizedEmbeddingsStorage<T>(project) {
  abstract fun traversePsiFile(file: PsiFile): List<T>

  protected fun collectEntities(): List<T> {
    val projectRootManager = ProjectRootManager.getInstance(project)
    val psiManager = PsiManager.getInstance(project)
    return runBlockingCancellable {
      // It's important that we do not block write actions here:
      // If the write action is invoked, the read action is restarted
      readAction {
        buildList {
          projectRootManager.contentSourceRoots.forEach { root ->
            VfsUtilCore.iterateChildrenRecursively(root, null) { virtualFile ->
              ProgressManager.checkCanceled()
              if (virtualFile.canonicalFile != null && virtualFile.isFile) {
                psiManager.findFile(virtualFile)?.also { file ->
                  val classes = traversePsiFile(file)
                  addAll(classes)
                }
              }
              return@iterateChildrenRecursively true
            }
          }
        }
      }
    }
  }
}