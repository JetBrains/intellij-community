// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.uv

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.python.pyproject.PY_PROJECT_TOML
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.getSdkAPI
import com.jetbrains.python.packaging.requirementsTxt.PythonRequirementTxtSdkUtils
import com.jetbrains.python.sdk.PythonSdkAdditionalData
import com.jetbrains.python.sdk.pySdkAdditionalData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import kotlin.io.path.exists

/**
 * How uv manages the packages of an SDK.
 *
 * The root dependency file that the SDK stores in [PythonSdkAdditionalData.requirementsFile] selects the mode. The file
 * `pyproject.toml` selects [Project]. Any other file selects [Pip].
 *
 * A new uv SDK always stores its file, in pip mode too. A stored file is a decision. An SDK without a stored file was
 * saved before the mode existed. [pinLegacyUvMode] resolves such an SDK from its working directory once.
 *
 * Public for the test fixtures that create a uv SDK. Everything that reads or resolves a mode stays internal.
 */
@ApiStatus.Internal
sealed interface UvMode {
  /** The root dependency file to store for this mode, relative to the working directory of the SDK. */
  val requirementsFile: Path

  /** A uv project. It has a `pyproject.toml` and a `uv.lock`. uv manages it with `uv add`, `uv remove`, `uv sync` and `uv lock`. */
  data object Project : UvMode {
    override val requirementsFile: Path = Path.of(PY_PROJECT_TOML)
  }

  /** A plain environment that uv manages with `uv pip`. [requirementsFile] names the requirements file. The file may not exist yet. */
  data class Pip(override val requirementsFile: Path = PythonSdkAdditionalData.REQUIREMENT_TXT_DEFAULT) : UvMode
}

/**
 * The mode of this SDK, read from the stored dependency file. This getter does no I/O.
 *
 * No stored file reads as [UvMode.Pip]. An SDK saved before the mode existed reads the same way until
 * [pinLegacyUvMode] has run for it.
 */
@get:ApiStatus.Internal
val Sdk.uvMode: UvMode
  get() = when (val fileName = pySdkAdditionalData.requirementsFile) {
    null -> UvMode.Pip()
    PY_PROJECT_TOML -> UvMode.Project
    else -> UvMode.Pip(Path.of(fileName))
  }

/** The mode of the SDK of this interpreter. See [Sdk.uvMode]. */
@get:ApiStatus.Internal
val PythonInterpreter.uvMode: UvMode
  @Suppress("DEPRECATION") get() = getSdkAPI().uvMode

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
 * Resolves the mode of an SDK that stores no dependency file, and pins a project.
 *
 * When the working directory holds a `pyproject.toml`, this function stores that file and returns [UvMode.Project].
 * Otherwise it stores nothing and returns [UvMode.Pip]. For an SDK that already stores a file, it returns [Sdk.uvMode].
 *
 * The lookup asks the VFS without a refresh, so the function runs on any thread. A file that the VFS has not seen counts
 * as absent. [migrateLegacyUvMode] refreshes the file first. The store commits asynchronously off the EDT. A caller that
 * needs the mode at once uses the returned value.
 */
internal fun pinLegacyUvMode(project: Project, sdk: Sdk): UvMode {
  val pyProjectToml = legacyPyProjectToml(sdk) ?: return sdk.uvMode
  if (VirtualFileManager.getInstance().findFileByNioPath(pyProjectToml) == null) return UvMode.Pip()
  PythonRequirementTxtSdkUtils.saveRequirementsTxtPath(project, sdk, UvMode.Project.requirementsFile)
  return UvMode.Project
}

/**
 * Refreshes the `pyproject.toml` of a legacy SDK from disk on the IO dispatcher, then calls [pinLegacyUvMode].
 * The VFS is current at that point, so the answer is final.
 */
@ApiStatus.Internal
suspend fun migrateLegacyUvMode(project: Project, sdk: Sdk): UvMode {
  legacyPyProjectToml(sdk)?.let { path ->
    withContext(Dispatchers.IO) { VirtualFileManager.getInstance().refreshAndFindFileByNioPath(path) }
  }
  return pinLegacyUvMode(project, sdk)
}

/**
 * The `pyproject.toml` path that decides the mode of a legacy SDK.
 * Returns `null` when the SDK stores a file or has no valid working directory.
 */
private fun legacyPyProjectToml(sdk: Sdk): Path? {
  val data = sdk.pySdkAdditionalData
  if (data.requirementsFile != null) return null
  return data.workingDirectory.takeIf { data.hasValidWorkingDirectory() }?.resolve(PY_PROJECT_TOML)
}
