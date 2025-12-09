package com.intellij.python.externalIndex

import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ex.WelcomeScreenProjectProvider
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.backend.workspace.workspaceModel
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.python.externalIndex.workspace.PyExternalIndexedFileEntity
import com.intellij.python.externalIndex.workspace.PyExternalIndexedFileEntitySource
import com.intellij.util.asSafely
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus

/**
 * Service for tracking and indexing external Python files in the non-modal welcome project.
 * That allows running inspections on the non-project files.
 *
 * Once a non-project file (does not belong to the source roots or library) opened in the editor,
 * the [PyExternalIndexedFileEntity] is created.
 * After that it is being indexed by [PyExternalFilesIndexService].
 *
 * Later, when the file is closed from the editor, the [PyExternalIndexedFileEntity] is removed from the index.
 *
 * @see WelcomeScreenCommandLineProjectOpenProcessor
 */
@Service(Service.Level.PROJECT)
@ApiStatus.Internal
class PyExternalFilesIndexService(private val project: Project, private val coroutineScope: CoroutineScope) {
  init {
    trackExternalFilesInEditor()
  }

  private fun trackExternalFilesInEditor() {
    // Only track external files in welcome screen projects
    if (!WelcomeScreenProjectProvider.isWelcomeScreenProject(project)) {
      return
    }
    val connection = project.messageBus.connect()
    connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
      override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
        // Add only if this file is currently active (selected) and is external
        val isSelected = source.selectedFiles.any { it == file }
        if (isSelected && file.isValid) {
          addFile(file)
        }
      }

      override fun fileClosed(source: FileEditorManager, file: VirtualFile) {
        // If file is not opened in any other tab/window anymore — remove from index
        val stillOpen = source.isFileOpen(file)
        if (!stillOpen) {
          removeFile(file)
        }
      }
    })
  }

  private fun addFile(file: VirtualFile) = coroutineScope.launch {
    if (!WelcomeScreenProjectProvider.isWelcomeScreenProject(project)) {
      return@launch
    }
    if (!(file.isValid && file.isExternalToProject(project) && file.isSupportedForExternalIndex())) {
      return@launch
    }

    val virtualFileUrlManager = project.workspaceModel.getVirtualFileUrlManager()
    val virtualFileUrl = file.toVirtualFileUrl(virtualFileUrlManager)

    project.workspaceModel.update("Add Non project file of welcome screen to storage") { storage ->
      if (storage.containsNonProjectFile(virtualFileUrl)) {
        return@update
      }
      storage.addEntity(PyExternalIndexedFileEntity(virtualFileUrl, PyExternalIndexedFileEntitySource))
    }
  }

  private fun removeFile(file: VirtualFile) {
    val virtualFileUrlManager = project.workspaceModel.getVirtualFileUrlManager()
    val virtualFileUrl = file.toVirtualFileUrl(virtualFileUrlManager)

    coroutineScope.launch {
      project.workspaceModel.update("Remove Non project file from storage") { storage ->
        storage.entitiesBySource { entitySource ->
          entitySource is PyExternalIndexedFileEntitySource
        }.filter {
          it.asSafely<PyExternalIndexedFileEntity>()?.file == virtualFileUrl
        }.forEach { storage.removeEntity(it) }
      }
    }
  }

  fun isFileAddedToNonProjectIndex(file: VirtualFile): Boolean {
    val virtualFileManager = project.workspaceModel.getVirtualFileUrlManager()
    val storage = project.workspaceModel.currentSnapshot
    return storage.containsNonProjectFile(file.toVirtualFileUrl(virtualFileManager))
  }

  companion object {
    private val SUPPORTED_FILE_EXTENSIONS = listOf("py", "ipynb")

    private fun EntityStorage.containsNonProjectFile(virtualFileUrl: VirtualFileUrl): Boolean {
      return this.entitiesBySource { entitySource ->
        entitySource is PyExternalIndexedFileEntitySource
      }.any {
        it.asSafely<PyExternalIndexedFileEntity>()?.file == virtualFileUrl
      }
    }

    private suspend fun VirtualFile.isExternalToProject(project: Project): Boolean = readAction {
      val index = ProjectFileIndex.getInstance(project)
      // Real external file: not in project content and not in any libraries
      !index.isInContent(this) && !index.isInLibraryClasses(this) && !index.isInLibrarySource(this)
    }

    private fun VirtualFile.isSupportedForExternalIndex(): Boolean {
      val ext = this.extension?.lowercase()
      return ext in SUPPORTED_FILE_EXTENSIONS
    }
  }
}

private class PyWelcomeScreenOpenedFilesListener : ProjectActivity {
  override suspend fun execute(project: Project) {
    // Initiate the service so it can start listening for files in the editor
    project.service<PyExternalFilesIndexService>()
  }
}
