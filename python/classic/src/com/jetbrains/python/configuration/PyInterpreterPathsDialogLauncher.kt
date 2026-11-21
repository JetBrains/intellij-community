// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.configuration

import com.intellij.openapi.application.EDT
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.toNioPathOrNull
import com.intellij.python.sdk.backend.NativePythonInterpreter
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.PythonInterpreterDetails
import com.intellij.python.sdk.backend.PythonInterpreterRoots
import com.intellij.python.sdk.backend.PythonInterpreterUpdateRequest
import com.intellij.python.sdk.backend.PythonInterpreterUpdater
import com.intellij.python.sdk.backend.TargetPythonInterpreter
import com.intellij.python.sdk.backend.classRoots
import com.intellij.python.sdk.backend.writePathMappings
import com.intellij.util.PathMappingSettings
import com.jetbrains.python.PyBundle
import com.jetbrains.python.sdk.updateInterpreterFacts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus

/**
 * Public entry point that opens the legacy [PythonPathDialog] for a given [PythonInterpreter].
 *
 * [PythonPathDialog] and [PythonPathEditor] are package-private to `com.jetbrains.python.configuration`,
 * so other packages, such as the All Interpreters page (PY-89840), cannot instantiate them directly. This wrapper runs
 * the flow `reset → showAndGet → write the facts → reload`, so the caller does not repeat it. The dialog edits the
 * facts of the interpreter, see [PythonInterpreterDetails], and no SDK.
 */
@ApiStatus.Internal
object PyInterpreterPathsDialogLauncher {
  /**
   * `PythonPathDialog` is a Swing `DialogWrapper`, so the show / edit phase runs on the EDT. The facts are written
   * after the dialog closes, and the `PythonInterpreterUpdater` call follows, so the caller's coroutine unblocks the EDT
   * the moment the dialog closes.
   */
  suspend fun open(project: Project, interpreter: PythonInterpreter, onPathsChanged: () -> Unit = {}): Boolean {
    val facts = PythonInterpreterDetails.getInstance(project).facts(interpreter)
    val fileManager = VirtualFileManager.getInstance()
    val classRoots = interpreter.classRoots.toList()
    val added = facts.addedPaths.mapNotNullTo(LinkedHashSet()) { fileManager.findFileByNioPath(it) }
    val excluded = facts.excludedPaths.mapNotNullTo(LinkedHashSet()) { fileManager.findFileByNioPath(it) }
    val sourceRoots = PythonInterpreterRoots.getInstance(project).sourceRoots(interpreter)
    val edit = withContext(Dispatchers.EDT) {
      // A target interpreter maps a path that the user adds on the target to a local copy.
      val remoteEditor = when (interpreter) {
        is NativePythonInterpreter -> null
        is TargetPythonInterpreter -> PyRemotePathEditor(project, interpreter)
      }
      val editor = remoteEditor ?: PythonPathEditor(
        PyBundle.message("python.sdk.configuration.tab.title"),
        OrderRootType.CLASSES,
        FileChooserDescriptorFactory.createAllButJarContentsDescriptor(),
      )
      editor.setModuleSourceRoots(sourceRoots)
      // Wire the "Reload paths" toolbar action inside the paths editor to a full interpreter
      // re-detection: `PythonInterpreterUpdater` walks the binary again and refreshes version +
      // site-packages / typeshed roots. Without this callback the toolbar button would only
      // refresh the editor's view of already-known roots, not re-scan the interpreter itself.
      editor.addReloadPathsActionCallback { PythonInterpreterUpdater.getInstance(project).request(interpreter, PythonInterpreterUpdateRequest(packages = true, background = false)) }
      val dialog = PythonPathDialog(project, editor)
      editor.reset(classRoots, added, excluded)
      if (!dialog.showAndGet()) return@withContext null
      if (!editor.isModified) return@withContext null
      // Copies: the editor keeps its sets on the EDT.
      PathsEdit(editor.addedPaths.toList(), editor.excludedPaths.toList(), remoteEditor?.takeIf { it.isPathMappingsChanged }?.pathMappings)
    } ?: return false
    writeUserPaths(project, interpreter, edit.added, edit.excluded)
    edit.pathMappings?.let { interpreter.writePathMappings(it) }
    updateInterpreterFacts(interpreter, project)
    onPathsChanged()
    return true
  }
}

/** What the user changed in the paths dialog: the added and excluded paths, and the path mappings of a target, if new. */
private class PathsEdit(val added: List<VirtualFile>, val excluded: List<VirtualFile>, val pathMappings: PathMappingSettings?)

/**
 * Writes [added] and [excluded], the paths that the user set in the paths dialog, as facts of [interpreter], see
 * [PythonInterpreterDetails.writeAddedPaths].
 */
@ApiStatus.Internal
suspend fun writeUserPaths(project: Project, interpreter: PythonInterpreter, added: Collection<VirtualFile>, excluded: Collection<VirtualFile>) {
  val details = PythonInterpreterDetails.getInstance(project)
  details.writeAddedPaths(interpreter, added.mapNotNull { it.toNioPathOrNull() })
  details.writeExcludedPaths(interpreter, excluded.mapNotNullTo(HashSet()) { it.toNioPathOrNull() })
}
