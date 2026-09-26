// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.configuration

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.OrderRootType
import com.jetbrains.python.PyBundle
import com.jetbrains.python.sdk.PythonSdkUpdater
import com.jetbrains.python.sdk.legacy.PythonSdkUtil
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
   * `PythonSdkUpdater` call hops onto `Dispatchers.Default` so the caller's coroutine unblocks the
   * EDT the moment the dialog closes.
   */
  suspend fun open(project: Project, sdk: Sdk, onPathsChanged: (Sdk) -> Unit = {}): Boolean {
    val strategy = PathEditorStrategy.forSdk(sdk)
    val (editor, modified) = withContext(Dispatchers.EDT) {
      val editor = strategy.createEditor(project, sdk)
      // Wire the "Reload paths" toolbar action inside the paths editor to a full interpreter
      // re-detection: `PythonSdkUpdater` walks the SDK binary again and refreshes version +
      // site-packages / typeshed roots. Without this callback the toolbar button would only
      // refresh the editor's view of already-known roots, not re-scan the interpreter itself.
      // Same wiring as legacy `PythonInterpreterMasterDetails.ShowPathsAction` (`reloadSdk`).
      editor.addReloadPathsActionCallback { PythonSdkUpdater.updateVersionAndPathsSynchronouslyAndScheduleRemaining(sdk, project) }
      val sdkModificator = sdk.sdkModificator
      val dialog = PythonPathDialog(project, editor)
      editor.reset(sdkModificator)
      if (!dialog.showAndGet()) return@withContext editor to false
      if (!editor.isModified) return@withContext editor to false
      editor.apply(sdkModificator)
      edtWriteAction { sdkModificator.commitChanges() }
      editor to true
    }
    if (!modified) return false
    withContext(Dispatchers.Default) {
      PythonSdkUpdater.updateVersionAndPathsSynchronouslyAndScheduleRemaining(sdk, project)
    }
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
