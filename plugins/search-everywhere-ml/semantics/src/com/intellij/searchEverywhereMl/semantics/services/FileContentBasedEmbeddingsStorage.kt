package com.intellij.searchEverywhereMl.semantics.services

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.isFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.searchEverywhereMl.semantics.indices.IndexableEntity

abstract class FileContentBasedEmbeddingsStorage<T : IndexableEntity>(project: Project)
  : DiskSynchronizedEmbeddingsStorage<T>(project), Disposable {
  abstract fun traversePsiFile(file: PsiFile): List<T>

  protected fun collectEntities(fileChangeListener: SemanticSearchFileContentChangeListener<T>): List<T> {
    val psiManager = PsiManager.getInstance(project)
    // It's important that we do not block write actions here:
    // If the write action is invoked, the read action is restarted
    return ReadAction.nonBlocking<List<T>> {
      buildList {
        ProjectRootManager.getInstance(project).contentSourceRoots.forEach { root ->
          VfsUtilCore.iterateChildrenRecursively(root, null) { virtualFile ->
            ProgressManager.checkCanceled()
            if (virtualFile.canonicalFile != null && virtualFile.isFile) {
              psiManager.findFile(virtualFile)?.also { file ->
                val classes = traversePsiFile(file)
                fileChangeListener.addEntityCountsForFile(virtualFile, classes)
                addAll(classes)
              }
            }
            return@iterateChildrenRecursively true
          }
        }
      }
    }.executeSynchronously()
  }

  override fun dispose() {}
}