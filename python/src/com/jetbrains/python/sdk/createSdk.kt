// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.intellij.execution.target.FullPathOnTarget
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.SdkAdditionalData
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.toEelApi
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.PythonInterpreterRegistry
import com.intellij.python.sdk.backend.getSdkAPI
import com.intellij.python.sdk.backend.registerTarget
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.project.PyProject
import com.jetbrains.python.project.project
import com.jetbrains.python.sdk.add.v2.EelFileSystem
import com.jetbrains.python.sdk.add.v2.FileSystem
import com.jetbrains.python.sdk.add.v2.PathHolder
import com.jetbrains.python.sdk.flavors.CPythonSdkFlavor
import com.jetbrains.python.sdk.flavors.PyFlavorAndData
import com.jetbrains.python.sdk.flavors.PyFlavorData
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor
import com.jetbrains.python.target.PyTargetAwareAdditionalData
import com.jetbrains.python.target.ui.TargetPanelExtension
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

// Those are tools to create SDK
// As PyCharm developer, do not call `addSdk` directly: use these tools only.


/**
 * Request to create a sdk either eel or target-based.
 * Once created, call [createSdk]
 */
internal sealed interface SdkCreationRequest<P, D : SdkAdditionalData> {
  val path: P
  val data: D

  data class EelSdk(
    override val path: PythonBinary,
    override val data: PythonSdkAdditionalData,
  ) : SdkCreationRequest<PythonBinary, PythonSdkAdditionalData>

  data class TargetSdk(
    override val path: FullPathOnTarget,
    override val data: PyTargetAwareAdditionalData,
  ) : SdkCreationRequest<FullPathOnTarget, PyTargetAwareAdditionalData>
}

/**
 * Advanced options. They have no effect now: a created interpreter is never in the SDK table, and
 * `PyProject.setPythonInterpreter` sets up its paths when it registers it. Remove them with their callers.
 */
@ApiStatus.Internal
data class SdkCreationAdvancedOpts(
  internal val persist: Boolean = true,
  val setupPaths: Boolean = true,
) {
  companion object {
    val DEFAULT: SdkCreationAdvancedOpts = SdkCreationAdvancedOpts()
  }
}

/**
 * The interpreter at [pythonBinaryPath] for [pyProject], not registered yet: set it with
 * `PyProject.setPythonInterpreter`. Use [com.jetbrains.python.sdk.add.v2.FileSystem.setupSdk] if possible.
 */
@ApiStatus.Internal
suspend fun createSdk(
  pyProject: PyProject,
  pythonBinaryPath: PathHolder.Eel,
  sdkAdditionalData: PythonSdkAdditionalData,
  suggestedSdkName: String? = null,
  advancedOpts: SdkCreationAdvancedOpts = SdkCreationAdvancedOpts.DEFAULT,
): PyResult<PythonInterpreter> =
  createSdkImpl(pyProject, SdkCreationRequest.EelSdk(pythonBinaryPath.path, sdkAdditionalData), suggestedSdkName, advancedOpts)

/**
 * The interpreter at [pythonBinaryPath] for [pyProject], not registered yet: set it with
 * `PyProject.setPythonInterpreter`. Use [com.jetbrains.python.sdk.add.v2.FileSystem.setupSdk] if possible.
 */
@ApiStatus.Internal
suspend fun createSdk(
  pyProject: PyProject,
  pythonBinaryPath: PathHolder.Target,
  sdkAdditionalData: PyTargetAwareAdditionalData,
  suggestedSdkName: String? = null,
  advancedOpts: SdkCreationAdvancedOpts = SdkCreationAdvancedOpts.DEFAULT,
): PyResult<PythonInterpreter> =
  createSdkImpl(pyProject, SdkCreationRequest.TargetSdk(pythonBinaryPath.pathString, sdkAdditionalData), suggestedSdkName, advancedOpts)

/**
 * [createSdk] for the [PyProject] of [moduleOrProject]. Without a [PyProject] yet, the owner is the working directory
 * of [sdkAdditionalData], else of [moduleOrProject]: the [PyProject] that appears there later owns the interpreter. The
 * new project wizard passes the directory of the project it creates.
 */
@ApiStatus.Internal
suspend fun createSdk(
  moduleOrProject: ModuleOrProject,
  pythonBinaryPath: PathHolder.Eel,
  sdkAdditionalData: PythonSdkAdditionalData,
  suggestedSdkName: String? = null,
  advancedOpts: SdkCreationAdvancedOpts = SdkCreationAdvancedOpts.DEFAULT,
): PyResult<PythonInterpreter> =
  SdkCreationRequest.EelSdk(pythonBinaryPath.path, sdkAdditionalData).createSdk(moduleOrProject, suggestedSdkName, advancedOpts)

/** [createSdk] for the [PyProject] of [moduleOrProject], see the overload for [PathHolder.Eel]. */
@ApiStatus.Internal
suspend fun createSdk(
  moduleOrProject: ModuleOrProject,
  pythonBinaryPath: PathHolder.Target,
  sdkAdditionalData: PyTargetAwareAdditionalData,
  suggestedSdkName: String? = null,
  advancedOpts: SdkCreationAdvancedOpts = SdkCreationAdvancedOpts.DEFAULT,
): PyResult<PythonInterpreter> =
  SdkCreationRequest.TargetSdk(pythonBinaryPath.pathString, sdkAdditionalData).createSdk(moduleOrProject, suggestedSdkName, advancedOpts)


/**
 * Please use [com.jetbrains.python.sdk.add.v2.FileSystem.setupSdk] instead
 */
internal suspend fun SdkCreationRequest<*, *>.createSdk(
  moduleOrProject: ModuleOrProject,
  suggestedSdkName: String? = null,
  advancedOpts: SdkCreationAdvancedOpts = SdkCreationAdvancedOpts.DEFAULT,
): PyResult<PythonInterpreter> {
  val ownerDir = moduleOrProject.findPyProject()?.baseDir
                 ?: pythonData.workingDirectory.takeIf { pythonData.hasValidWorkingDirectory() }
                 ?: moduleOrProject.workingDirectory
                 ?: return PyResult.localizedError(PyBundle.message("python.sdk.project.working.directory.not.found"))
  return createSdkImpl(moduleOrProject.project, ownerDir, this, suggestedSdkName)
}




/**
 * Use this API only if you do not know SDK type in advance (in most cases you do, please prefer [createSdk]).
 * The interpreter is not registered: set it with `PyProject.setPythonInterpreter`.
 */
@ApiStatus.Internal
suspend fun createLocalSdkGuessingTypeByPath(
  homePath: PythonBinary,
  moduleOrProject: ModuleOrProject,
  suggestedSdkName: String? = null,
): PyResult<PythonInterpreter> =
  createSdkGuessingTypeByPath(PathHolder.Eel(homePath),
                              EelFileSystem(homePath.getEelDescriptor().toEelApi()),
                              moduleOrProject,
                              null,
                              suggestedSdkName)


/**
 * Use this API only if you do not know SDK type in advance (in most cases you do, please prefer [createSdk])
 */
internal suspend fun <P : PathHolder> createSdkGuessingTypeByPath(
  homePath: P,
  fileSystem: FileSystem<P>,
  moduleOrProject: ModuleOrProject,
  targetPanelExtension: TargetPanelExtension?,
  suggestedSdkName: String? = null,
): PyResult<PythonInterpreter> {
  val flavorAndData = fileSystem.flavorAndDataOf(homePath)

  val workingDirectory = moduleOrProject.workingDirectory
                         ?: return PyResult.localizedError(PyBundle.message("python.sdk.project.working.directory.not.found"))

  val newPythonInterpreter = fileSystem.setupSdk(
    moduleOrProject = moduleOrProject,
    pythonBinaryPath = homePath,
    sdkAdditionalData = PythonSdkAdditionalData(
      flavorAndData,
      workingDirectory,
    ),
    targetPanelExtension = targetPanelExtension,
    suggestedSdkName = suggestedSdkName
  ).getOr { return it }

  moduleOrProject.project.excludeInnerVirtualEnv(newPythonInterpreter.getSdkAPI())

  return PyResult.success(newPythonInterpreter)
}

/**
 * The interpreter of [request] for [pyProject], not registered yet. See [createSdkImpl] with an owner directory.
 */
private suspend fun createSdkImpl(
  pyProject: PyProject,
  request: SdkCreationRequest<*, *>,
  suggestedSdkName: String?,
  @Suppress("UNUSED_PARAMETER") advancedOpts: SdkCreationAdvancedOpts,
): PyResult<PythonInterpreter> = createSdkImpl(pyProject.project, pyProject.baseDir, request, suggestedSdkName)

/**
 * The caller has the binary and the data of the environment, so it builds the interpreter itself, and nothing probes
 * the environment again. The interpreter is not registered: `PyProject.setPythonInterpreter` registers it for the
 * [PyProject] at [ownerDir]. A target is registered in [project] first, so the ref can name it.
 */
private suspend fun createSdkImpl(
  project: Project,
  ownerDir: Path,
  request: SdkCreationRequest<*, *>,
  suggestedSdkName: String?,
): PyResult<PythonInterpreter> {
  val pythonBinary = when (request) {
    is SdkCreationRequest.EelSdk -> PathHolder.Eel(request.path)
    is SdkCreationRequest.TargetSdk -> {
      val target = request.data.targetEnvironmentConfiguration
                   ?: return PyResult.localizedError(PyBundle.message("python.sdk.python.executable.not.found", request.path))
      registerTarget(project, target)
      PathHolder.Target(request.path)
    }
  }
  return PythonInterpreterRegistry.getInstance(project).newPythonInterpreter(ownerDir, pythonBinary, request.pythonData, suggestedSdkName)
}

/** The data of the SDK. Both kinds of request carry [PythonSdkAdditionalData]. */
private val SdkCreationRequest<*, *>.pythonData: PythonSdkAdditionalData
  get() = when (this) {
    is SdkCreationRequest.EelSdk -> data
    is SdkCreationRequest.TargetSdk -> data
  }

/**
 * The flavor of the local interpreter at [pythonBinary], guessed from its path. Only a flavor without data can be
 * guessed: a conda env, for example, needs the path to conda.
 */
internal suspend fun guessLocalFlavorAndData(pythonBinary: Path): PyFlavorAndData<PyFlavorData.Empty, *> = withContext(Dispatchers.IO) {
  val detectedFlavor = PythonSdkFlavor.tryDetectFlavorByLocalPath(pythonBinary)
  val flavor = if (detectedFlavor != null && detectedFlavor.flavorDataClass.isInstance(PyFlavorData.Empty)) {
    @Suppress("UNCHECKED_CAST") // Checked a line above
    detectedFlavor as CPythonSdkFlavor<PyFlavorData.Empty>
  }
  else {
    PythonSdkFlavor.UnknownFlavor.INSTANCE
  }
  PyFlavorAndData(PyFlavorData.Empty, flavor)
}
