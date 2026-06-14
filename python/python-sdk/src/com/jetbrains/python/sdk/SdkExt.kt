// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.intellij.execution.target.TargetBasedSdkAdditionalData
import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.getOrCreateUserDataUnsafe
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.ex.temp.TempFileSystem
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor
import com.jetbrains.python.sdk.impl.buildPresentationInfo
import com.jetbrains.python.sdk.legacy.PythonSdkUtil.isPythonSdk
import com.jetbrains.python.target.PyTargetAwareAdditionalData
import org.jetbrains.annotations.ApiStatus.Internal
import java.nio.file.InvalidPathException
import java.nio.file.Path
import kotlin.io.path.Path

@get:Internal
val BASE_DIR: Key<Path> = Key.create("PYTHON_PROJECT_BASE_PATH")

private val pySdkKey = Key.create<Boolean>("isPythonSdk")

/**
 * Asserts that this SDK has [PythonSdkType].
 *
 * @throws IllegalArgumentException if the SDK type is not a Python SDK.
 */
@Internal
fun Sdk.requirePythonSdk() {
  require(getOrCreateUserDataUnsafe(pySdkKey) { isPythonSdk(this, true) }) { "Can't be called only for PythonSdkType and not for $sdkType" }
}

/**
 * Associates this SDK with the given [module] by storing the module's base directory path
 * in [PythonSdkAdditionalData.associatedModulePath] and committing the change.
 *
 */
@Internal
suspend fun Sdk.setAssociationToModule(module: Module) {
  requirePythonSdk()

  val path = module.baseDir?.path
  assert(path != null) { "Module $module has not paths, and can't be associated" }
  setAssociationToPath(path)
}

/**
 * Sets the [PythonSdkAdditionalData.associatedModulePath] to [path] and commits the change.
 *
 * Pass `null` to clear the association.
 */
@Internal
suspend fun Sdk.setAssociationToPath(path: String?) {
  requirePythonSdk()

  val data = pySdkAdditionalData
    .also {
      it.associatedModulePath = path
    }

  val modificator = sdkModificator
  modificator.sdkAdditionalData = data

  writeAction {
    modificator.commitChanges()
  }
}


@Internal
fun Sdk.isRunAsRootViaSudo(): Boolean {
  val data = getSdkAdditionalData()
  return data is PyTargetAwareAdditionalData && data.isRunAsRootViaSudo()
}

/**
 * Returns whether this [Sdk] seems valid or not.
 *
 * The actual check logic is located in [PythonSdkFlavor.sdkSeemsValid] and its overrides. In general, the method check whether the path to
 * the Python binary stored in this [Sdk] exists and the corresponding file can be executed. This check can be performed both locally and
 * on a target. The latter case takes place when [PythonSdkAdditionalData] of this [Sdk] implements [PyTargetAwareAdditionalData] and the
 * corresponding target provides file system operations (see [com.jetbrains.python.pathValidation.ValidationRequest]).
 *
 *
 * @see PythonSdkFlavor.sdkSeemsValid
 */
@get:Internal
val Sdk.isSdkSeemsValid: Boolean
  get() {
    if (!isPythonSdk(this, true)) return false
    if (this.sdkAdditionalData is PyInvalidSdk) {
      return false
    }

    val pythonSdkAdditionalData = pySdkAdditionalData
    return pythonSdkAdditionalData.flavorAndData.sdkSeemsValid(this, targetEnvConfiguration)
  }

@Internal
fun Sdk?.isTargetBased(): Boolean = this != null && targetEnvConfiguration != null

/**
 *  Additional data if sdk is target-based
 */
@get:Internal
val Sdk.targetAdditionalData: PyTargetAwareAdditionalData?
  get():PyTargetAwareAdditionalData? = sdkAdditionalData as? PyTargetAwareAdditionalData

/**
 * Returns target environment if configuration is target api based
 */

@get:Internal
val Sdk.targetEnvConfiguration: TargetEnvironmentConfiguration?
  get():TargetEnvironmentConfiguration? = (sdkAdditionalData as? TargetBasedSdkAdditionalData)?.targetEnvironmentConfiguration


@Internal
fun Sdk.pyInterpreterPresentation(customName: String? = null): PythonInterpreterPresentation = buildPresentationInfo(customName)


private val SDK_ERROR_REPORTED = Key.create<Boolean>("pySdkErrorReported")

@get:Internal
val Sdk.associatedModuleDir: VirtualFile?
  get() {
    val nioPath = associatedModuleNioPath ?: return null
    return VirtualFileManager.getInstance().findFileByNioPath(nioPath) ?: TempFileSystem.getInstance().findFileByNioFile(nioPath)
  }

@get:Internal
val Sdk.associatedModuleNioPath: Path?
  get() =
    try {
      associatedModulePath?.let { Path(it) }
    }
    catch (e: InvalidPathException) {
      if (getUserData(SDK_ERROR_REPORTED) != true) {
        thisLogger().warn("Can't convert ${associatedModulePath} to path", e)
        putUserData(SDK_ERROR_REPORTED, true)
      }
      null
    }

@get:Internal
val Sdk.associatedModulePath: String?
  // TODO: Support .project associations
  get() = associatedPathFromAdditionalData /*?: associatedPathFromDotProject*/

private val Sdk.associatedPathFromAdditionalData: String?
  get() = (sdkAdditionalData as? PythonSdkAdditionalData)?.associatedModulePath

/**
 * Every Python SDK has [PythonSdkAdditionalData].
 * It should be created along with sdk (for that reason, you shouldn't create Python SDK directly, * but use `createSdk` functions)
 * For most cases SDK is known to be Python, but if it is not, use [com.jetbrains.python.sdk.legacy.PythonSdkUtil.isPythonSdk].
 */
@get:Internal
val Sdk.pySdkAdditionalData: PythonSdkAdditionalData
  get() {
    requirePythonSdk()
    return sdkAdditionalData as? PythonSdkAdditionalData ?: error(
      """
      Sdk $this doesn't have an additional data: it was created by buggy code.
      Please use ${com.jetbrains.python.sdk.add.v2.FileSystem<*>::setupSdk} or one of its implementors directly to create an SDK
      """.trimIndent())
  }

