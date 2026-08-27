// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.intellij.execution.target.FullPathOnTarget
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.SdkAdditionalData
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.toEelApi
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.MessageError
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.sdk.add.v2.EelFileSystem
import com.jetbrains.python.sdk.add.v2.FileSystem
import com.jetbrains.python.sdk.add.v2.PathHolder
import com.jetbrains.python.sdk.add.v2.PyProjectCreateHelpers
import com.jetbrains.python.sdk.flavors.CPythonSdkFlavor
import com.jetbrains.python.sdk.flavors.PyFlavorAndData
import com.jetbrains.python.sdk.flavors.PyFlavorData
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor
import com.jetbrains.python.sdk.flavors.UnixPythonSdkFlavor
import com.jetbrains.python.target.PyTargetAwareAdditionalData
import com.jetbrains.python.target.ui.TargetPanelExtension
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus

// Those are tools to create SDK
// As PyCharm developer, do not call `addSdk` directly: use these tools only.


/**
 * Request to create a sdk either eel or target-based.
 * Once created, call [createSdk]
 */
@ApiStatus.Internal
sealed interface SdkCreationRequest<P, D : SdkAdditionalData> {
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
 * Advanced options, do not change them unless you know what you are doing.
 *
 * [persist] sdk (add it to [com.intellij.openapi.projectRoots.impl.ProjectJdkImpl]) or not.
 * [setupPaths] means "to calculate various SDK paths", call SDK updater and so on.
 */
@ApiStatus.Internal
data class SdkCreationAdvancedOpts(internal val persist: Boolean = true, val setupPaths: Boolean = true) {
  companion object {
    val DEFAULT: SdkCreationAdvancedOpts = SdkCreationAdvancedOpts()
  }
}

/**
 * Kinda low-level API to create SDK. Use [com.jetbrains.python.sdk.add.v2.FileSystem.setupSdk] if possible.
 */
@ApiStatus.Internal
suspend fun createSdk(
  pythonBinaryPath: PathHolder.Eel,
  sdkAdditionalData: PythonSdkAdditionalData,
  suggestedSdkName: String? = null,
  advancedOpts: SdkCreationAdvancedOpts = SdkCreationAdvancedOpts.DEFAULT,
): Result<Sdk, MessageError> =
  createSdkImpl(SdkCreationRequest.EelSdk(pythonBinaryPath.path, sdkAdditionalData), suggestedSdkName, advancedOpts)

/**
 * Kinda low-level API to create SDK. Use [com.jetbrains.python.sdk.add.v2.FileSystem.setupSdk] if possible.
 */
@ApiStatus.Internal
suspend fun createSdk(
  pythonBinaryPath: PathHolder.Target,
  sdkAdditionalData: PyTargetAwareAdditionalData,
  suggestedSdkName: String? = null,
  advancedOpts: SdkCreationAdvancedOpts = SdkCreationAdvancedOpts.DEFAULT,
): Result<Sdk, MessageError> =
  createSdkImpl(SdkCreationRequest.TargetSdk(pythonBinaryPath.pathString, sdkAdditionalData), suggestedSdkName, advancedOpts)

/**
 * Please use [com.jetbrains.python.sdk.add.v2.FileSystem.setupSdk] instead
 */
@ApiStatus.Internal
suspend fun SdkCreationRequest<*, *>.createSdk(
  suggestedSdkName: String? = null,
  advancedOpts: SdkCreationAdvancedOpts = SdkCreationAdvancedOpts.DEFAULT,
): Result<Sdk, MessageError> = createSdkImpl(this, suggestedSdkName, advancedOpts)


/**
 * Use this API only if you do not know SDK type in advance (in most cases you do, please prefer [createSdk]).
 * This function creates and persists SDL
 */
@ApiStatus.Internal
suspend fun createLocalSdkGuessingTypeByPath(
  homePath: PythonBinary,
  moduleOrProject: ModuleOrProject,
  suggestedSdkName: String? = null,
): PyResult<Sdk> =
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
): PyResult<Sdk> {
  val flavorAndData = when (homePath) {
    is PathHolder.Eel -> withContext(Dispatchers.IO) {
      val detectedFlavor = PythonSdkFlavor.tryDetectFlavorByLocalPath(homePath.path)
      // We only support flavours without data (i.e. we can't detect conda as we have no conda path)
      val flavor = if (detectedFlavor != null && detectedFlavor.flavorDataClass.isInstance(PyFlavorData.Empty)) {
        @Suppress("UNCHECKED_CAST") // Checked a line above
        detectedFlavor as CPythonSdkFlavor<PyFlavorData.Empty>
      }
      else {
        PythonSdkFlavor.UnknownFlavor.INSTANCE
      }
      PyFlavorAndData(PyFlavorData.Empty, flavor)
    }
    // Target is always UNIX
    is PathHolder.Target -> PyFlavorAndData(PyFlavorData.Empty, UnixPythonSdkFlavor.getInstance())
  }

  val workingDirectory = moduleOrProject.workingDirectory
                         ?: return PyResult.localizedError(PyBundle.message("python.sdk.project.working.directory.not.found"))

  val newSdk = fileSystem.setupSdk(
    project = moduleOrProject.project,
    pythonBinaryPath = homePath,
    sdkAdditionalData = PythonSdkAdditionalData(
      flavorAndData,
      workingDirectory,
    ),
    targetPanelExtension = targetPanelExtension,
    suggestedSdkName = suggestedSdkName
  ).getOr { return it }

  val module = PyProjectCreateHelpers.getModule(moduleOrProject, newSdk.homeDirectory)
  if (module != null) {
    newSdk.setAssociationToModule(module)
  }

  moduleOrProject.project.excludeInnerVirtualEnv(newSdk)

  return PyResult.success(newSdk)
}

private suspend fun createSdkImpl(
  request: SdkCreationRequest<*, *>,
  suggestedSdkName: String? = null,
  advancedOpts: SdkCreationAdvancedOpts,
): Result<Sdk, MessageError> {
  val sdkType = PythonSdkType.getInstance()
  val existingSdks = PythonSdkUtil.getAllSdks()

  val pythonPath = when (request) {
    is SdkCreationRequest.EelSdk -> {
      val sdkAdditionalData = request.data
      val pythonBinaryPath = request.path

      // A usual interpreter has one SDK, and it may exist already. A remote one never reuses an SDK. Its path names
      // a file on a machine that the path does not identify. So two remote SDKs can share a path and still be
      // different interpreters. Docker is the worst case.
      if (sdkAdditionalData !is PyRemoteSdkAdditionalDataMarker) {
        val reused = findSdkToAdopt(pythonBinaryPath, existingSdks) {
          suggestedSdkName ?: sdkType.suggestSdkName(null, pythonBinaryPath.toString())
        }
        if (reused != null) return PyResult.success(reused.adoptData(sdkAdditionalData))
      }

      val pythonBinaryVirtualFile = withContext(Dispatchers.IO) {
        VirtualFileManager.getInstance().refreshAndFindFileByNioPath(request.path)
      } ?: return PyResult.localizedError(PyBundle.message("python.sdk.python.executable.not.found", pythonBinaryPath))

      pythonBinaryVirtualFile.path
    }

    is SdkCreationRequest.TargetSdk -> request.path
  }

  @Suppress("SETUP_SDK_DIRECTLY")  // This is the only place calling this method is allowed
  val sdk = SdkConfigurationUtil.createSdk(
    existingSdks,
    pythonPath,
    sdkType,
    request.data,
    suggestedSdkName
  )


  if (advancedOpts.persist) {
    edtWriteAction {
      makeSureNameIsUnique(sdk)
      ProjectJdkTable.getInstance().addJdk(sdk)
    }
  }
  if (advancedOpts.setupPaths) {
    sdkType.setupSdkPaths(sdk)
  }
  return Result.success(sdk)
}

/**
 * The usual SDK that already stands for the interpreter at [pythonBinaryPath], or `null` when none does. A usual SDK is
 * one whose data carries no [PyRemoteSdkAdditionalDataMarker].
 *
 * The IDE keeps one usual SDK for each interpreter, not one for each interpreter and tool. A `.venv` that poetry
 * created, and uv then adopted, is one environment. A second SDK for it reads as a second interpreter in every list the
 * IDE shows. A remote SDK is never a candidate, because its path does not say which machine holds the file. Two remote
 * SDKs can share a path and still be different interpreters.
 *
 * Older builds also keyed on the tool, so one path can carry several SDKs already. The SDK named [preferredName] wins,
 * because that is the name a new SDK gets here. If no SDK has that name, the first one wins, so the answer is stable.
 * [preferredName] is read only when there is more than one candidate, because it reads the file system.
 */
private fun findSdkToAdopt(pythonBinaryPath: PythonBinary, existingSdks: List<Sdk>, preferredName: () -> String): Sdk? {
  // Compared as paths, not as strings, because `c:\windows` and `c:/Windows` name one file.
  val candidates = existingSdks.filter {
    it.sdkAdditionalData !is PyRemoteSdkAdditionalDataMarker && it.pythonBinaryPath().successOrNull == pythonBinaryPath
  }
  if (candidates.size < 2) return candidates.firstOrNull()
  val name = preferredName()
  return candidates.firstOrNull { it.name == name } ?: candidates.first()
}

/**
 * Points this SDK at [data] and returns it. The SDK keeps the name it has.
 *
 * The name is how the user refers to this interpreter, in each run configuration and in every list the IDE shows. The
 * environment it names has not moved. Only what the IDE records about that environment changes: the tool that manages
 * it now, and that tool's own data.
 */
private suspend fun Sdk.adoptData(data: PythonSdkAdditionalData): Sdk = apply {
  edtWriteAction {
    val modificator = sdkModificator
    modificator.sdkAdditionalData = data
    modificator.commitChanges()
  }
}

@RequiresWriteLock
private fun makeSureNameIsUnique(sdk: Sdk) {
  val name = sdk.name
  var i = 1
  while (ProjectJdkTable.getInstance().findJdk(sdk.name) != null) {
    val m = sdk.sdkModificator
    m.name = "$name@$i"
    i += 1
    m.commitChanges()
  }
}