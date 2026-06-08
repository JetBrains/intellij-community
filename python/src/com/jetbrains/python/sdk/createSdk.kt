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
import java.nio.file.InvalidPathException
import kotlin.io.path.Path

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
): Result<Sdk, MessageError> = createSdkImpl(SdkCreationRequest.EelSdk(pythonBinaryPath.path, sdkAdditionalData), suggestedSdkName, advancedOpts)

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
  suggestedSdkName: String? = null
): PyResult<Sdk> =
  createSdkGuessingTypeByPath(PathHolder.Eel(homePath), EelFileSystem(homePath.getEelDescriptor().toEelApi()), moduleOrProject, null, true, suggestedSdkName)


/**
 * Use this API only if you do not know SDK type in advance (in most cases you do, please prefer [createSdk])
 */
internal suspend fun <P : PathHolder> createSdkGuessingTypeByPath(
  homePath: P,
  fileSystem: FileSystem<P>,
  moduleOrProject: ModuleOrProject,
  targetPanelExtension: TargetPanelExtension?,
  isAssociateWithModule: Boolean,
  suggestedSdkName: String? = null
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


  val newSdk = fileSystem.setupSdk(
    project = moduleOrProject.project,
    pythonBinaryPath = homePath,
    sdkAdditionalData = PythonSdkAdditionalData(flavorAndData),
    targetPanelExtension = targetPanelExtension,
    suggestedSdkName = suggestedSdkName
  ).getOr { return it }

  val module = PyProjectCreateHelpers.getModule(moduleOrProject, newSdk.homeDirectory)
  if (isAssociateWithModule && module != null) {
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

      // for remote sdks we can't distinguish target environment configurations (docker the worst case)
      existingSdks.find {

        // Paths can't be compared as strings as c:\windows != c:/Windows, so we convert then to NIO Paths.
        val homePath = try {
          it.homePath?.let { home -> Path(home) }
        }
        catch (_: InvalidPathException) {
          null
        }

        it.sdkAdditionalData?.javaClass == sdkAdditionalData.javaClass && homePath == pythonBinaryPath
      }?.let {
        return PyResult.success(it)
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