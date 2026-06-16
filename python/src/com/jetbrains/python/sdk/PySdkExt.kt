// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.intellij.ide.projectView.actions.MarkRootsManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.python.PyBundle
import com.jetbrains.python.isCondaVirtualEnv
import com.jetbrains.python.isNonToolVirtualEnv
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor
import com.jetbrains.python.sdk.legacy.PythonSdkUtil
import com.jetbrains.python.sdk.readOnly.PythonSdkReadOnlyProvider
import com.jetbrains.python.target.PyTargetAwareAdditionalData
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Internal
import java.nio.file.Path
import kotlin.io.path.div

@Internal
fun configurePythonSdk(project: Project, module: Module, sdk: Sdk) {
  // in case module contains root of the project we consider it as a project wide interpreter
  if (project.basePath == module.baseDir?.path) {
    project.pythonSdk = sdk
  }

  module.pythonSdk = sdk
  module.excludeInnerVirtualEnv(sdk)
}


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
 * Please set sdk on module level: [Module.pythonSdk]
 */
@set:ApiStatus.Obsolete
var Project.pythonSdk: Sdk?
  get() {
    val sdk = ProjectRootManager.getInstance(this).projectSdk
    return when (sdk?.sdkType) {
      is PythonSdkType -> sdk
      else -> null
    }
  }
  set(value) {
    val application = ApplicationManager.getApplication()
    application.invokeAndWait {
      application.runWriteAction {
        ProjectRootManager.getInstance(this).projectSdk = value
      }
    }
  }

@Internal
fun Module.excludeInnerVirtualEnv(sdk: Sdk) {
  val root = getInnerVirtualEnvRoot(sdk.pyRichSdk()) ?: return

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

@Internal
fun getInnerVirtualEnvRoot(sdk: PyRichSdk): VirtualFile? = sdk.pythonHomePath?.let {
  LocalFileSystem.getInstance().findFileByNioFile(it)
}

internal val Sdk.isSystemWide: Boolean
  get() = !PythonSdkUtil.isRemote(this) && !this.isNonToolVirtualEnv && !this.isCondaVirtualEnv


@get:Internal
val Sdk.isReadOnly: Boolean
  get() = PythonSdkReadOnlyProvider.isReadOnly(this)

@get:Internal
val Sdk.readOnlyErrorMessage: String
  get() = PythonSdkReadOnlyProvider.getReadOnlyMessage(this) ?: PyBundle.message("python.sdk.read.only", name)

internal val Sdk.sdkFlavor: PythonSdkFlavor<*> get() = pySdkAdditionalData.flavor

private fun Sdk.isLocatedInsideModule(module: Module): Boolean {
  val moduleDir = module.baseDir
  val sdkDir = homeDirectory
  return moduleDir != null && sdkDir != null && VfsUtil.isAncestor(moduleDir, sdkDir, true)
}


private val PY_VER_REGEX = Regex(""".*python(\d\.\d)""")

@Deprecated("See com.intellij.python.junit5Tests.env.services.internal.impl.PythonWithLanguageLevelImplTest.testSunnyDay")
@get:Internal
private val Sdk.guessedLanguageLevel: LanguageLevel?
  get() {
    val path = homePath ?: return null
    val result = PY_VER_REGEX.find(path) ?: return null
    val versionString = result.groupValues.getOrNull(1) ?: return null
    return LanguageLevel.fromPythonVersion(versionString)
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
  get() =
    Path.of(PathManager.getSystemPath()) /
    Path.of(PythonSdkUtil.REMOTE_SOURCES_DIR_NAME) /
    Path.of(when (val data = sdkAdditionalData) {
              is PyTargetAwareAdditionalData -> data.uuid.toString()
              else -> error("Only legacy and remote SDK and target-based SDKs are supported")
            }.hashCode().toString())

