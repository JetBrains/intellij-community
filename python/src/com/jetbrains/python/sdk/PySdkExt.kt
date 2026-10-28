// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.intellij.ide.projectView.actions.MarkRootsManager
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.python.pyproject.model.evolution.findMainEvoPyProjectIfReady
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.detectedEnvironment
import com.intellij.python.sdk.backend.getSdkAPI
import com.intellij.python.sdk.backend.pythonInterpreterResult
import com.intellij.python.sdk.backend.remoteSourcesLocalPath
import com.jetbrains.python.PyBundle
import com.jetbrains.python.isCondaVirtualEnv
import com.jetbrains.python.isNonToolVirtualEnv
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor
import com.jetbrains.python.sdk.legacy.PythonSdkUtil
import com.jetbrains.python.sdk.readOnly.PythonSdkReadOnlyProvider
import com.jetbrains.python.target.PyTargetAwareAdditionalData
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.ApiStatus.ScheduledForRemoval
import org.jetbrains.annotations.Nls
import java.nio.file.Path

internal fun resetSystemWideSdksDetectors() {
  PythonSdkFlavor.getApplicableFlavors(false).forEach(PythonSdkFlavor<*>::dropCaches)
}

@Internal
fun Sdk.isAssociatedWithModule(module: Module?): Boolean {
  val basePath = module?.baseDir?.path
  val associatedPath = associatedModulePath
  if (basePath != null && associatedPath == basePath) return true
  if (isAssociatedWithAnotherModule(module)) return false
  return (module != null && isLocatedInsideModule(module)) || containsModuleName(module)
}

@Internal
fun Sdk.isAssociatedWithAnotherModule(module: Module?): Boolean {
  val basePath = module?.baseDir?.path ?: return false
  val associatedPath = associatedModulePath ?: return false
  return basePath != associatedPath
}

/**
 * The interpreter of the main Python project, for a caller that cannot suspend and runs after startup, such as a
 * lesson or an action. `null` before the first snapshot, and when the project has no main Python project. A coroutine
 * calls [com.intellij.python.pyproject.model.evolution.findMainPythonInterpreter], which waits.
 */
@Internal
fun Project.mainPythonInterpreterIfReady(): PythonInterpreter? = findMainEvoPyProjectIfReady()?.interpreter

/**
 * The SDK of the interpreter of the main Python project, for external plugins. There is no project interpreter: an
 * interpreter belongs to a Python project. `null` before the first snapshot, and when the project has no main Python
 * project.
 */
@Deprecated(
  "An interpreter belongs to a Python project. Use the interpreter of the PyProject, or findMainPythonInterpreter for the main one.",
  ReplaceWith("mainPythonInterpreterIfReady()?.getSdkAPI()", "com.intellij.python.sdk.backend.getSdkAPI"),
)
val Project.pythonSdk: Sdk?
  @ScheduledForRemoval
  @Deprecated("An interpreter belongs to a Python project. Use the interpreter of the PyProject.")
  @Suppress("DEPRECATION") // It still answers an SDK.
  get() = mainPythonInterpreterIfReady()?.getSdkAPI()

@Internal
fun Module.excludeInnerVirtualEnv(sdk: Sdk) {
  val root = sdk.pythonInterpreterResult().successOrNull?.detectedEnvironment?.pythonHomePath?.let {
    VirtualFileManager.getInstance().findFileByNioPath(it)
  } ?: return

  runInEdt {
    MarkRootsManager.modifyRoots(this, arrayOf(root)) { vFile, entry ->
      entry.addExcludeFolder(vFile)
    }
  }
}

internal fun Project.excludeInnerVirtualEnv(sdk: Sdk) {
  val binary = sdk.homeDirectory ?: return
  ModuleUtil.findModuleForFile(binary, this)?.excludeInnerVirtualEnv(sdk)
}

internal val Sdk.isSystemWide: Boolean
  get() = !PythonSdkUtil.isRemote(this) && !this.isNonToolVirtualEnv && !this.isCondaVirtualEnv


/** Whether packages cannot be installed into this interpreter, for example one inside a Docker image. */
@get:Internal
val PythonInterpreter.isReadOnly: Boolean
  get() = PythonSdkReadOnlyProvider.isReadOnly(this)

/** Why packages cannot be installed into this interpreter. See [isReadOnly]. */
@get:Internal
val PythonInterpreter.readOnlyErrorMessage: @Nls String
  @Suppress("DEPRECATION") // The fallback names the SDK.
  get() = PythonSdkReadOnlyProvider.getReadOnlyMessage(this) ?: PyBundle.message("python.sdk.read.only", getSdkAPI().name)

internal val Sdk.sdkFlavor: PythonSdkFlavor<*> get() = pySdkAdditionalData.flavor

private fun Sdk.isLocatedInsideModule(module: Module): Boolean {
  val moduleDir = module.baseDir
  val sdkDir = homeDirectory
  return moduleDir != null && sdkDir != null && VfsUtil.isAncestor(moduleDir, sdkDir, true)
}


private fun Sdk.containsModuleName(module: Module?): Boolean {
  val path = homePath ?: return false
  val name = module?.name ?: return false
  return path.contains(name, true)
}


@JvmName("getOrCreateAdditionalData")
fun getOrCreateAdditionalDataOld(sdk: Sdk): PythonSdkAdditionalData = sdk.pySdkAdditionalData

/**
 * Where a "remote_sources" folder for certain SDK is stored
 */
@get:Internal
internal val Sdk.remoteSourcesLocalPath: Path
  get() = remoteSourcesLocalPath(sdkAdditionalData as? PyTargetAwareAdditionalData
                                 ?: error("Only legacy and remote SDK and target-based SDKs are supported"))

