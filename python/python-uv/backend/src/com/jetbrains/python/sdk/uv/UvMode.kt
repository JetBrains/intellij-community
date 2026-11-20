// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.uv

import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.python.sdk.backend.requirementsPath
import com.intellij.python.sdk.backend.writeRequirementsPath
import com.intellij.python.pyproject.PY_PROJECT_TOML
import com.intellij.python.sdk.backend.PythonInterpreter
import com.jetbrains.python.packaging.utils.PyPackageCoroutine
import com.jetbrains.python.sdk.PythonSdkAdditionalData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import kotlin.io.path.exists

/**
 * How uv manages the packages of an interpreter.
 *
 * The root dependency file that the interpreter stores selects the mode, see [PythonInterpreter.requirementsPath].
 * The file `pyproject.toml` selects [Project]. Any other file selects [Pip].
 *
 * A new uv interpreter always stores its file, in pip mode too. A stored file is a decision. An interpreter without a
 * stored file was saved before the mode existed. [pinLegacyUvMode] resolves such an interpreter from its owner
 * directory once.
 *
 * Public for the test fixtures that create a uv SDK. Everything that reads or resolves a mode stays internal.
 */
@ApiStatus.Internal
sealed interface UvMode {
  /** The root dependency file to store for this mode, relative to the owner directory of the interpreter. */
  val requirementsFile: Path

  /** A uv project. It has a `pyproject.toml` and a `uv.lock`. uv manages it with `uv add`, `uv remove`, `uv sync` and `uv lock`. */
  data object Project : UvMode {
    override val requirementsFile: Path = Path.of(PY_PROJECT_TOML)
  }

  /** A plain environment that uv manages with `uv pip`. [requirementsFile] names the requirements file. The file may not exist yet. */
  data class Pip(override val requirementsFile: Path = PythonSdkAdditionalData.REQUIREMENT_TXT_DEFAULT) : UvMode
}

/**
 * The mode of this library, read from its stored dependency file, see [PythonInterpreter.requirementsPath]. This
 * function does no I/O.
 *
 * No stored file reads as [UvMode.Pip]. An interpreter saved before the mode existed reads the same way until
 * [pinLegacyUvMode] has run for it.
 */
@ApiStatus.Internal
fun PythonInterpreter.uvMode(): UvMode {
  val path = requirementsPath ?: return UvMode.Pip()
  return if (path.fileName?.toString() == PY_PROJECT_TOML) UvMode.Project else UvMode.Pip(path)
}

/** Whether [workingDir] holds a `pyproject.toml`. One file stat, on the IO dispatcher. */
@ApiStatus.Internal
suspend fun hasPyProjectToml(workingDir: Path): Boolean = withContext(Dispatchers.IO) {
  workingDir.resolve(PY_PROJECT_TOML).exists()
}

/** The mode a directory calls for: [UvMode.Project] when it holds a `pyproject.toml`, else [UvMode.Pip]. */
@ApiStatus.Internal
suspend fun detectUvMode(workingDir: Path): UvMode =
  if (hasPyProjectToml(workingDir)) UvMode.Project else UvMode.Pip()

/** Whether an SDK of this mode in [workingDir] needs `uv init` first. [UvMode.Project] needs it when the directory has no `pyproject.toml`. */
internal suspend fun UvMode.needsInit(workingDir: Path): Boolean = when (this) {
  UvMode.Project -> !hasPyProjectToml(workingDir)
  is UvMode.Pip -> false
}

/**
 * Resolves the mode of an interpreter that stores no dependency file, and pins a project.
 *
 * When the owner directory holds a `pyproject.toml`, this function stores that file and returns [UvMode.Project].
 * Otherwise it stores nothing and returns [UvMode.Pip]. For an interpreter that already stores a file, it returns its
 * [uvMode].
 *
 * The lookup asks the VFS without a refresh, so the function runs on any thread. A file that the VFS has not seen counts
 * as absent. [migrateLegacyUvMode] refreshes the file first. The store writes asynchronously. A caller that needs the
 * mode at once uses the returned value.
 */
internal fun pinLegacyUvMode(interpreter: PythonInterpreter): UvMode {
  val pyProjectToml = legacyPyProjectToml(interpreter) ?: return interpreter.uvMode()
  if (VirtualFileManager.getInstance().findFileByNioPath(pyProjectToml) == null) return UvMode.Pip()
  PyPackageCoroutine.launch(interpreter.project) { interpreter.writeRequirementsPath(UvMode.Project.requirementsFile) }
  return UvMode.Project
}

/**
 * Refreshes the `pyproject.toml` of a legacy interpreter from disk on the IO dispatcher, then calls [pinLegacyUvMode].
 * The VFS is current at that point, so the answer is final.
 */
@ApiStatus.Internal
suspend fun migrateLegacyUvMode(interpreter: PythonInterpreter): UvMode {
  legacyPyProjectToml(interpreter)?.let { path ->
    withContext(Dispatchers.IO) { VirtualFileManager.getInstance().refreshAndFindFileByNioPath(path) }
  }
  return pinLegacyUvMode(interpreter)
}

/**
 * The `pyproject.toml` path that decides the mode of a legacy interpreter: the one in its owner directory. Returns
 * `null` when the interpreter stores a file.
 */
private fun legacyPyProjectToml(interpreter: PythonInterpreter): Path? {
  if (interpreter.requirementsPath != null) return null
  return interpreter.ownerDir.resolve(PY_PROJECT_TOML)
}
