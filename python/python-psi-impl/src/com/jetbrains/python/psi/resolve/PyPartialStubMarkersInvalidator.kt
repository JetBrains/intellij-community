// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.resolve

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileCopyEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import com.intellij.util.asDisposable
import com.jetbrains.python.sdk.legacy.PythonSdkUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

@OptIn(FlowPreview::class)
@Service(Service.Level.PROJECT)
class PyPartialStubMarkersInvalidator(
  private val project: Project,
  private val coroutineScope: CoroutineScope,
) {
  fun subscribe() {
    val invalidationRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val parentDisposable = coroutineScope.asDisposable()

    coroutineScope.launch {
      invalidationRequests
        .debounce(INVALIDATION_DELAY)
        .collectLatest {
          clearPathCaches(project)
        }
    }

    project.messageBus.connect(parentDisposable).subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
      override fun after(events: MutableList<out VFileEvent>) {
        if (events.any { affectsPartialStubMarkers(project, it) }) {
          invalidationRequests.tryEmit(Unit)
        }
      }
    })

    EditorFactory.getInstance().eventMulticaster.addDocumentListener(object : DocumentListener {
      override fun documentChanged(event: DocumentEvent) {
        val file = FileDocumentManager.getInstance().getFile(event.document) ?: return
        if (isProjectPartialStubMarkerFile(project, file)) {
          invalidationRequests.tryEmit(Unit)
        }
      }
    }, parentDisposable)
  }

  private fun clearPathCaches(project: Project) {
    if (project.isDisposed) return
    val sdks = linkedSetOf<Sdk>()

    for (module in ModuleManager.getInstance(project).modules) {
      PythonModulePathCache.getInstance(module).clearCache()
      PythonSdkUtil.findPythonSdk(module)?.let(sdks::add)
    }

    ProjectRootManager.getInstance(project).projectSdk
      ?.takeIf(PythonSdkUtil::isPythonSdk)
      ?.let(sdks::add)

    for (sdk in sdks) {
      PythonSdkPathCache.getInstance(project, sdk).clearCache()
    }
  }

  private fun isPartialStubMarkerFileName(fileName: String?): Boolean {
    return fileName == "py.typed"
  }

  private fun affectsPartialStubMarkers(project: Project, event: VFileEvent): Boolean {
    if (event.file?.let { isProjectPartialStubMarkerFile(project, it) } == true) {
      return true
    }

    return when (event) {
      is VFileCopyEvent ->
        isPartialStubMarkerFileName(event.newChildName) &&
        isProjectRelevantLocation(project, event.newParent)
      is VFilePropertyChangeEvent ->
        event.isRename &&
        (isPartialStubMarkerFileName(event.oldValue as? String) || isPartialStubMarkerFileName(event.newValue as? String)) &&
        event.file.parent?.let { isProjectRelevantLocation(project, it) } == true
      else -> false
    }
  }

  private fun isProjectPartialStubMarkerFile(project: Project, file: VirtualFile): Boolean {
    return isPartialStubMarkerFileName(file.name) && isProjectRelevantLocation(project, file)
  }

  private fun isProjectRelevantLocation(project: Project, file: VirtualFile): Boolean {
    if (!project.isInitialized || !file.isValid) {
      return false
    }

    val index = ProjectFileIndex.getInstance(project)
    return index.isInProject(file)
  }

  companion object {
    private val INVALIDATION_DELAY = 300.milliseconds
  }
}

internal class PyPartialStubMarkersInvalidatorActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    project.service<PyPartialStubMarkersInvalidator>().subscribe()
  }
}
