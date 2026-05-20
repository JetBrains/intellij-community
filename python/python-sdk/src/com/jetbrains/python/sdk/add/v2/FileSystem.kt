// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.execution.target.TargetEnvironmentRequest
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextComponentAccessor
import com.intellij.python.community.execService.BinaryToExec
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.jetbrains.python.PyToolUIInfo
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.pathValidation.PlatformAndRoot
import com.jetbrains.python.sdk.PythonSdkAdditionalData
import com.jetbrains.python.target.ui.TargetPanelExtension
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import java.nio.file.Path
import javax.swing.JComponent

@ApiStatus.Internal
interface FileSystem<P : PathHolder> {
  val isReadOnly: Boolean
  val isBrowsable: Boolean
  val isLocal: Boolean
  val userReadableName: @NonNls String
  val platformAndRoot: PlatformAndRoot

  fun parsePath(raw: String): PyResult<P>
  suspend fun validateExecutable(path: P): PyResult<Unit>
  suspend fun fileExists(path: P): Boolean

  @RequiresEdt
  fun <T> configureFileBrowseEditor(
    fieldAccessor: TextComponentAccessor<ComboBox<T>>,
    comboBox: ComboBox<T>,
    browseTitle: @Nls String,
    parentComponent: JComponent,
  )

  /**
   * [pathToPython] has to be system (not venv) if set [requireSystemPython]
   */
  suspend fun getSystemPythonFromSelection(pathToPython: P, requireSystemPython: Boolean): PyResult<DetectedSelectableInterpreter<P>>

  /**
   * Creates Python SDK and persists it. Please, do not use any other tool to create Python SDKs unless you know what you are doing.
   * [pythonBinaryPath] is either [Path] (for local and future eel-based SDKs) or target (for old, remote SDKs).
   * [targetPanelExtension] is irrelevant, ignore it.
   * [sdkAdditionalData] must have [com.jetbrains.python.sdk.flavors.PythonSdkFlavor].
   * If you have no idea what flavor is, use `createLocalSdkGuessingTypeByPath`
   */
  suspend fun setupSdk(
    project: Project?,
    pythonBinaryPath: P,
    sdkAdditionalData: PythonSdkAdditionalData,
    targetPanelExtension: TargetPanelExtension?,
    suggestedSdkName: String?,
  ): PyResult<Sdk>

  fun createTargetRequest(): TargetEnvironmentRequest

  suspend fun validateVenv(homePath: P): PyResult<Unit>
  suspend fun suggestVenv(projectPath: Path): PyResult<P>
  suspend fun wrapSdk(sdk: Sdk): SdkWrapper<P>
  suspend fun detectSelectableVenv(projectPathPrefix: Path): List<DetectedSelectableInterpreter<P>>
  fun preferredInterpreterBasePath(): P? = null
  fun resolvePythonBinary(pythonHome: P): P?
  fun resolvePythonHome(pythonBinary: P): P
  fun getVenvName(pythonHome: P): String?

  fun getBinaryToExec(path: P, workingDir: Path? = null): BinaryToExec
  suspend fun getHomePath(): P?

  /**
   * Normalizes the given path to a remote-compatible format. For eel-based systems it just returns a path, for target-based ones we
   * sometimes want to have a path on target. For target dialogs we can receive a string that is a WSL path, so we should normalize it to
   * represent a path on target.
   */
  fun normalizePathToRemote(path: P): P

  suspend fun detectEnvironments(workingDir: Path, uiInfoGetter: (P) -> PyToolUIInfo?): List<DetectedSelectableInterpreter<P>>
  suspend fun detectTool(
    toolName: String,
    additionalSearchPaths: List<P> = listOf(),
    filter: (P) -> Boolean = { true },
  ): P?

  /** Resolves [pathComponents] under the value of environment variable [prefixEnvVar]. Null if the variable is unset or unreadable. */
  suspend fun getFullPath(prefixEnvVar: String, pathComponents: List<String>): P?

  /** Resolves [pathComponents] under the user's home directory. Null if the home cannot be determined. */
  suspend fun getFullPathFromHome(pathComponents: List<String>): P?

  /**
   * Resolves [dirName] relative to [workingDir]. For eel-based filesystems this is a plain path resolve; for target-based filesystems it
   * shells out to `pwd` inside [workingDir] on the remote machine so that the returned path is in the
   * target's namespace rather than the host's. Null if the remote lookup fails.
   */
  suspend fun resolveInWorkingDir(workingDir: Path, dirName: String): P?
}
