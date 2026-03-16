package com.intellij.python.pyproject.model.internal.platformBridge

import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.FileIndexFacade
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.AsyncFileListener
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.python.pyproject.model.internal.PyProjectScopeService
import com.jetbrains.python.venvReader.VirtualEnvReader.Companion.DEFAULT_VIRTUALENV_DIRNAME
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Excludes [DEFAULT_VIRTUALENV_DIRNAME] from [project] as soon as it appears in index, should only be called once
 */
internal suspend fun startVenvExclusion(project: Project) {
  VirtualFileManager.getInstance().addAsyncFileListener(project.service<PyProjectScopeService>().scope) { events ->
    if (events.none { it is VFileCreateEvent || it is VFileMoveEvent }) {
      // No need to check anything if no file created
      null
    }
    else {
      object : AsyncFileListener.ChangeApplier {
        override fun afterVfsChange() {
          excludeEnvs(project)
        }
      }
    }
  }
  excludeEnvs(project).join()
}

private fun excludeEnvs(project: Project): Job =
  project.service<PyProjectScopeService>().scope.launch(Dispatchers.Default) {
    mutex.withLock {
      val dirs = readAction { FilenameIndex.getVirtualFilesByName(DEFAULT_VIRTUALENV_DIRNAME, GlobalSearchScope.allScope(project)) }
      for (venvToExclude in dirs) {
        val module = readAction { FileIndexFacade.getInstance(project).getModuleForFile(venvToExclude) } ?: continue
        val rootManager = ModuleRootManager.getInstance(module)
        if (venvToExclude !in rootManager.excludeRoots) {
          writeAction {
            val model = rootManager.modifiableModel
            val currentRoot = model.contentEntries.firstOrNull { root ->
              root.file?.let { VfsUtilCore.isAncestor(it, venvToExclude, false) } == true
            }
            if (currentRoot != null) {
              currentRoot.addExcludeFolder(venvToExclude)
              model.commit()
            }else {
              model.dispose()
            }
          }
        }
      }
    }
  }

private val mutex = Mutex()
