// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.intellij.execution.target.FullPathOnTarget
import com.intellij.execution.target.TargetConfigurationWithLocalFsAccess
import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.ide.projectView.actions.MarkRootsManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.python.venv.sdk.flavors.VirtualEnvSdkFlavor
import com.jetbrains.python.PyBundle
import com.jetbrains.python.isCondaVirtualEnv
import com.jetbrains.python.isNonToolVirtualEnv
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.run.PythonInterpreterTargetEnvironmentFactory
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor
import com.jetbrains.python.sdk.legacy.PythonSdkUtil
import com.jetbrains.python.sdk.readOnly.PythonSdkReadOnlyProvider
import com.jetbrains.python.target.PyTargetAwareAdditionalData
import com.jetbrains.python.target.createDetectedSdk
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Internal
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.pathString

private data class TargetAndPath(
  val target: TargetEnvironmentConfiguration?,
  val path: FullPathOnTarget?,
)

@Internal
fun configurePythonSdk(project: Project, module: Module, sdk: Sdk) {
  // in case module contains root of the project we consider it as a project wide interpreter
  if (project.basePath == module.baseDir?.path) {
    project.pythonSdk = sdk
  }

  module.pythonSdk = sdk
  module.excludeInnerVirtualEnv(sdk)
}

/**
 * Detects system-wide Python SDKs available in the current environment.
 *
 * **Deprecation Notice**
 *
 * This method relies on the outdated [com.jetbrains.python.sdk.flavors.PyFlavorData] concept, which is not compatible with the modern
 * [EelApi] used throughout the platform.
 *
 * **Recommended Alternative**
 *
 * Use [SystemPythonService.findSystemPythons] instead for discovering Python interpreters in dedicated environments with proper EelApi
 * integration.
 *
 * If you only want to get list of SDKs, use [PythonSdkUtil.getAllSdks]
 *
 * @param context used to get [BASE_DIR] in [VirtualEnvSdkFlavor.suggestLocalHomePaths]
 */
@Deprecated("PyDetectedSdk will be dropped soon, use SystemPythonService", level = DeprecationLevel.ERROR)
@JvmOverloads
fun detectSystemWideSdks(
  module: Module?,
  existingSdks: List<Sdk>,
  context: UserDataHolder = UserDataHolderBase(),
): List<PyDetectedSdk> {
  if (module != null && module.isDisposed) return emptyList()
  val targetModuleSitsOn = module?.let { PythonInterpreterTargetEnvironmentFactory.getTargetModuleResidesOn(it) }
  val existingPaths = existingSdks.mapTo(HashSet()) { TargetAndPath(it.targetEnvConfiguration, it.homePath) }
  return PythonSdkFlavor.getApplicableFlavors(false)
    .flatMap { flavor -> flavor.detectSdks(module, context, targetModuleSitsOn, existingPaths) }
    .sortedWith(compareBy<PyDetectedSdk>({ it.guessedLanguageLevel },
                                         { it.homePath }).reversed())
}


@Deprecated("Will be dropped soon along with PyDetectedSDK, do not use")
private fun PythonSdkFlavor<*>.detectSdks(
  module: Module?,
  context: UserDataHolder,
  targetModuleSitsOn: TargetConfigurationWithLocalFsAccess?,
  existingPaths: HashSet<TargetAndPath>,
): List<PyDetectedSdk> =
  detectSdkPaths(module, context, targetModuleSitsOn, existingPaths)
    .map { createDetectedSdk(it, targetModuleSitsOn?.asTargetConfig, this) }


@Deprecated("Will be dropped soon along with PyDetectedSDK, do not use")
private fun PythonSdkFlavor<*>.detectSdkPaths(
  module: Module?,
  context: UserDataHolder,
  targetModuleSitsOn: TargetConfigurationWithLocalFsAccess?,
  existingPaths: HashSet<TargetAndPath>,
): List<String> =
  suggestLocalHomePaths(module, context)
    .mapNotNull {
      // If a module sits on target, this target maps its path.
      if (targetModuleSitsOn == null) it.pathString else targetModuleSitsOn.getTargetPathIfLocalPathIsOnTarget(it)
    }
    .filter { TargetAndPath(targetModuleSitsOn?.asTargetConfig, it) !in existingPaths }

internal fun resetSystemWideSdksDetectors() {
  PythonSdkFlavor.getApplicableFlavors(false).forEach(PythonSdkFlavor<*>::dropCaches)
}

@Internal
fun detectVirtualEnvs(module: Module?, existingSdks: List<Sdk>, context: UserDataHolder): List<PyRichSdk> =
  filterSuggestedPaths(VirtualEnvSdkFlavor.getInstance(), existingSdks, module, context)

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

private fun Sdk.isLocatedInsideBaseDir(baseDir: Path?): Boolean {
  val homePath = homePath ?: return false
  val basePath = baseDir?.toString() ?: return false
  return FileUtil.isAncestor(basePath, homePath, true)
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

private fun filterSuggestedPaths(
  flavor: PythonSdkFlavor<*>,
  existingSdks: List<Sdk>,
  module: Module?,
  context: UserDataHolder,
  mayContainCondaEnvs: Boolean = false,
): List<PyRichSdk> {
  val targetModuleSitsOn = module?.let { PythonInterpreterTargetEnvironmentFactory.getTargetModuleResidesOn(it) }
  val existingPaths = existingSdks.mapTo(HashSet()) { TargetAndPath(it.targetEnvConfiguration, it.homePath) }
  val baseDirFromContext = context.getUserData(BASE_DIR)
  return flavor.suggestLocalHomePaths(module, context)
    .asSequence()
    .filterNot { TargetAndPath(targetModuleSitsOn?.asTargetConfig, it.toString()) in existingPaths }
    .distinct()
    .mapNotNull {
      if (targetModuleSitsOn == null) it.pathString else targetModuleSitsOn.getTargetPathIfLocalPathIsOnTarget(it)
    }
    .map { createDetectedSdk(it, targetModuleSitsOn?.asTargetConfig, flavor).pyRichSdk() }
    .sortedWith(
      compareBy(
        { !it.isAssociatedWithModule(module) && !it.isLocatedInsideBaseDir(baseDirFromContext) },
        { sdk ->
          if (!mayContainCondaEnvs) false
          else when (val env = sdk.pythonEnvironment) {
            is PythonEnvironment.Conda -> !env.isBase
            is PythonEnvironment.Venv, is PythonEnvironment.SystemPython, null -> true
          }
        },
        { it.homePath }
      )
    )
    .toList()
}

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

