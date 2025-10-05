// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.types

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.ThrottledLogger
import com.intellij.openapi.fileEditor.FileDocumentManagerListener
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiTreeChangeAdapter
import com.intellij.psi.PsiTreeChangeEvent
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.ProjectScope
import com.intellij.util.ForcefulReparseModificationTracker
import java.util.concurrent.TimeUnit

@Service(Service.Level.PROJECT)
class PyLibraryModificationTracker(project: Project) : ModificationTracker, Disposable {
  private val myProjectRootManager: ModificationTracker = ProjectRootManager.getInstance(project)
  private val myDumbServiceModificationTracker: ModificationTracker = DumbService.getInstance(project).modificationTracker
  private val myForcefulReparseModificationTracker: ModificationTracker = ForcefulReparseModificationTracker.getInstance() // PsiClass from libraries may become invalid on reparse
  private val myOnContentReloadModificationTracker: SimpleModificationTracker = SimpleModificationTracker()
  private val creationStack = Throwable()
  val projectLibraryScope: GlobalSearchScope = ProjectScope.getLibrariesScope(project)

  init {
    val connection = project.getMessageBus().connect(this)
    PsiManager.getInstance(project).addPsiTreeChangeListener(object : PsiTreeChangeAdapter() {
      override fun childrenChanged(event: PsiTreeChangeEvent) {
        val file = event.file ?: return
        val virtualFile = file.virtualFile ?: return
        if (isLibraryFile(virtualFile)) {
          myOnContentReloadModificationTracker.incModificationCount()
        }
      }
    }, this)

    connection.subscribe<FileDocumentManagerListener>(FileDocumentManagerListener.TOPIC, object : FileDocumentManagerListener {
      override fun fileWithNoDocumentChanged(file: VirtualFile) {
        if (!project.isInitialized()) {
          THROTTLED_LOG.warn("SearchScope.contains(file) would log an error because WorkspaceFileIndex is not yet initialized. " +
                             "Probably LibraryModificationTracker was created too early. " +
                             "See LibraryModificationTracker creation stacktrace: ", creationStack)
          return
        }
        if (isLibraryFile(file)) {
          myOnContentReloadModificationTracker.incModificationCount()
        }
      }
    })
  }

  private fun isLibraryFile(file: VirtualFile): Boolean {
    return "pyi" == file.extension || projectLibraryScope.contains(file)
  }

  override fun getModificationCount(): Long {
    return (myProjectRootManager.getModificationCount()
            + myDumbServiceModificationTracker.getModificationCount()
            + myForcefulReparseModificationTracker.getModificationCount()
            + myOnContentReloadModificationTracker.getModificationCount())
  }


  override fun dispose() {
  }

  companion object {
    private val THROTTLED_LOG = ThrottledLogger(Logger.getInstance(PyLibraryModificationTracker::class.java), TimeUnit.SECONDS.toMillis(30))

    fun getInstance(project: Project): PyLibraryModificationTracker = project.service()
  }
}