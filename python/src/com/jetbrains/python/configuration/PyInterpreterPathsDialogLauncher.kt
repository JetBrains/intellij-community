// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.configuration

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.toNioPathOrNull
import com.intellij.python.sdk.backend.PythonInterpreterDetails
import com.intellij.python.sdk.backend.PythonInterpreterRegistry
import com.intellij.python.sdk.backend.PythonInterpreterRoots
import com.intellij.python.sdk.backend.PythonInterpreterSdks
import com.intellij.python.sdk.backend.PythonInterpreterUpdateRequest
import com.intellij.python.sdk.backend.PythonInterpreterUpdater
import com.jetbrains.python.PyBundle
import com.jetbrains.python.sdk.legacy.PythonSdkUtil
import com.jetbrains.python.sdk.updateInterpreterFacts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus

/**
 * Public entry point that opens the legacy [PythonPathDialog] for a given [Sdk].
 *
 * [PythonPathDialog] and [PythonPathEditor] are package-private to `com.jetbrains.python.configuration`,
 * so downstream modules (redesigned "All Interpreters" page — PY-89840) can't instantiate them
 * directly. This wrapper mirrors [PythonInterpreterMasterDetails.ShowPathsAction]'s legacy flow
 * (`reset → showAndGet → apply → commitChanges → reload`) so the new page keeps the exact same
 * commit / reload semantics without duplicating the four-step orchestration on the caller side.
 */
@ApiStatus.Internal
object PyInterpreterPathsDialogLauncher {
  /**
   * `PythonPathDialog` is a Swing `DialogWrapper`, so the show / edit phase runs on the EDT. The
   * commit uses `edtWriteAction` — the coroutine-friendly EDT write helper — and the follow-up
   * `PythonInterpreterUpdater` call hops onto `Dispatchers.Default` so the caller's coroutine unblocks the
   * EDT the moment the dialog closes.
   */
  suspend fun open(project: Project, sdk: Sdk, onPathsChanged: (Sdk) -> Unit = {}): Boolean {
    val strategy = PathEditorStrategy.forSdk(sdk)
    val (editor, modified) = withContext(Dispatchers.EDT) {
      val editor = strategy.createEditor(project, sdk)
      editor.setModuleSourceRoots(moduleSourceRootsOf(project, sdk))
      // Wire the "Reload paths" toolbar action inside the paths editor to a full interpreter
      // re-detection: `PythonInterpreterUpdater` walks the SDK binary again and refreshes version +
      // site-packages / typeshed roots. Without this callback the toolbar button would only
      // refresh the editor's view of already-known roots, not re-scan the interpreter itself.
      // Same wiring as legacy `PythonInterpreterMasterDetails.ShowPathsAction` (`reloadSdk`).
      editor.addReloadPathsActionCallback { PythonInterpreterUpdater.getInstance(project).request(sdk, PythonInterpreterUpdateRequest(packages = true, background = false)) }
      val sdkModificator = sdk.sdkModificator
      val dialog = PythonPathDialog(project, editor)
      editor.reset(sdkModificator)
      if (!dialog.showAndGet()) return@withContext editor to false
      if (!editor.isModified) return@withContext editor to false
      editor.apply(sdkModificator)
      edtWriteAction { sdkModificator.commitChanges() }
      writeUserPaths(project, sdk, editor.addedPaths, editor.excludedPaths)
      editor to true
    }
    if (!modified) return false
    PythonInterpreterRegistry.getInstance(project).interpreterOf(sdk)?.let { updateInterpreterFacts(it, project) }
    onPathsChanged(sdk)
    return true
  }
}

/**
 * Which [PythonPathEditor] to build for an SDK — local vs remote. Two concrete impls avoid a
 * per-call `isRemote(sdk)` check spreading across the launcher's own body and any future helper.
 */
private sealed interface PathEditorStrategy {
  fun createEditor(project: Project, sdk: Sdk): PythonPathEditor

  object Local : PathEditorStrategy {
    override fun createEditor(project: Project, sdk: Sdk): PythonPathEditor =
      PythonPathEditor(
        PyBundle.message("python.sdk.configuration.tab.title"),
        OrderRootType.CLASSES,
        FileChooserDescriptorFactory.createAllButJarContentsDescriptor(),
      )
  }

  object Remote : PathEditorStrategy {
    override fun createEditor(project: Project, sdk: Sdk): PythonPathEditor = PyRemotePathEditor(project, sdk)
  }

  companion object {
    fun forSdk(sdk: Sdk): PathEditorStrategy = if (PythonSdkUtil.isRemote(sdk)) Remote else Local
  }
}


/**
 * The paths of the `sys.path` of [sdk] that are source roots of a module of [project], not roots of the interpreter, see
 * [PythonInterpreterDetails.sourceRoots]. Only the interpreter library flow has them.
 */
private fun moduleSourceRootsOf(project: Project, sdk: Sdk): Collection<VirtualFile> {
  val key = PythonInterpreterSdks.getInstance(project).keyOf(sdk) ?: return emptyList()
  val interpreter = PythonInterpreterRegistry.getInstance(project).resolveIfReady(key) ?: return emptyList()
  return PythonInterpreterRoots.getInstance(project).sourceRoots(interpreter)
}

/**
 * Writes [added] and [excluded], the paths that the user set in the paths dialog, as facts of the interpreter of [sdk],
 * see [PythonInterpreterDetails.writeAddedPaths]. The SDK commit of the dialog keeps what is SDK data, such as the path
 * mappings of a remote interpreter. Does nothing for an SDK that names no interpreter of [project].
 */
@ApiStatus.Internal
fun writeUserPaths(project: Project, sdk: Sdk, added: Collection<VirtualFile>, excluded: Collection<VirtualFile>) {
  val key = PythonInterpreterSdks.getInstance(project).keyOf(sdk) ?: return
  val interpreter = PythonInterpreterRegistry.getInstance(project).resolveIfReady(key) ?: return
  val details = PythonInterpreterDetails.getInstance(project)
  details.writeAddedPaths(interpreter, added.mapNotNull { it.toNioPathOrNull() })
  details.writeExcludedPaths(interpreter, excluded.mapNotNullTo(HashSet()) { it.toNioPathOrNull() })
}
