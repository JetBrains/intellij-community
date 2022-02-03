/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.sdk

import com.intellij.execution.ExecutionException
import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.PathUtil
import com.intellij.webcore.packaging.PackagesNotificationPanel
import com.jetbrains.python.PyBundle
import com.jetbrains.python.packaging.ui.PyPackageManagementService
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.remote.PyRemoteSdkAdditionalDataBase
import com.jetbrains.python.sdk.flavors.CondaEnvSdkFlavor
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor
import com.jetbrains.python.sdk.flavors.VirtualEnvSdkFlavor
import com.jetbrains.python.target.PyTargetAwareAdditionalData
import com.jetbrains.python.ui.PyUiUtil
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * @author vlan
 */

val BASE_DIR: Key<Path> = Key.create("PYTHON_PROJECT_BASE_PATH")

fun findAllPythonSdks(baseDir: Path?): List<Sdk> {
  val context: UserDataHolder = UserDataHolderBase()
  if (baseDir != null) {
    context.putUserData(BASE_DIR, baseDir)
  }
  val existing = PythonSdkUtil.getAllSdks()
  return detectCondaEnvs(null, existing, context) + detectVirtualEnvs(null, existing, context) + findBaseSdks(existing, null, context)
}

fun findBaseSdks(existingSdks: List<Sdk>, module: Module?, context: UserDataHolder): List<Sdk> {
  val existing = filterSystemWideSdks(existingSdks)
    .sortedWith(PreferredSdkComparator.INSTANCE)
    .filterNot { PythonSdkUtil.isBaseConda(it.homePath) }

  val detected = detectSystemWideSdks(module, existingSdks, context).filterNot { PythonSdkUtil.isBaseConda(it.homePath) }
  return existing + detected
}

fun mostPreferred(sdks: List<Sdk>): Sdk? = sdks.minWithOrNull(PreferredSdkComparator.INSTANCE)

fun filterSystemWideSdks(existingSdks: List<Sdk>): List<Sdk> {
  return existingSdks.filter { it.sdkType is PythonSdkType && it.isSystemWide }
}

@JvmOverloads
fun detectSystemWideSdks(module: Module?, existingSdks: List<Sdk>, context: UserDataHolder = UserDataHolderBase()): List<PyDetectedSdk> {
  if (module != null && module.isDisposed) return emptyList()
  val existingPaths = existingSdks.map { it.homePath }.toSet()
  return PythonSdkFlavor.getApplicableFlavors(false)
    .asSequence()
    .flatMap { it.suggestHomePaths(module, context).asSequence() }
    .filter { it !in existingPaths }
    .map { PyDetectedSdk(it) }
    .sortedWith(compareBy<PyDetectedSdk>({ it.guessedLanguageLevel },
                                         { it.homePath }).reversed())
    .toList()
}

fun resetSystemWideSdksDetectors() {
  PythonSdkFlavor.getApplicableFlavors(false).forEach(PythonSdkFlavor::dropCaches)
}

fun detectVirtualEnvs(module: Module?, existingSdks: List<Sdk>, context: UserDataHolder): List<PyDetectedSdk> =
  filterSuggestedPaths(VirtualEnvSdkFlavor.getInstance().suggestHomePaths(module, context), existingSdks, module, context)

fun filterSharedCondaEnvs(module: Module?, existingSdks: List<Sdk>): List<Sdk> {
  return existingSdks.filter { it.sdkType is PythonSdkType && PythonSdkUtil.isConda(it) && !it.isAssociatedWithAnotherModule(module) }
}

fun detectCondaEnvs(module: Module?, existingSdks: List<Sdk>, context: UserDataHolder): List<PyDetectedSdk> =
  filterSuggestedPaths(CondaEnvSdkFlavor.getInstance().suggestHomePaths(module, context), existingSdks, module, context, true)

fun filterAssociatedSdks(module: Module, existingSdks: List<Sdk>): List<Sdk> {
  return existingSdks.filter { it.sdkType is PythonSdkType && it.isAssociatedWithModule(module) }
}

fun detectAssociatedEnvironments(module: Module, existingSdks: List<Sdk>, context: UserDataHolder): List<PyDetectedSdk> {
  val virtualEnvs = detectVirtualEnvs(module, existingSdks, context).filter { it.isAssociatedWithModule(module) }
  val condaEnvs = detectCondaEnvs(module, existingSdks, context).filter { it.isAssociatedWithModule(module) }
  return virtualEnvs + condaEnvs
}

fun createSdkByGenerateTask(generateSdkHomePath: Task.WithResult<String, ExecutionException>,
                            existingSdks: List<Sdk>,
                            baseSdk: Sdk?,
                            associatedProjectPath: String?,
                            suggestedSdkName: String?): Sdk? {
  val homeFile = try {
    val homePath = ProgressManager.getInstance().run(generateSdkHomePath)
    StandardFileSystems.local().refreshAndFindFileByPath(homePath) ?: throw ExecutionException(
      PyBundle.message("python.sdk.directory.not.found", homePath)
    )
  }
  catch (e: ExecutionException) {
    showSdkExecutionException(baseSdk, e, PyBundle.message("python.sdk.failed.to.create.interpreter.title"))
    return null
  }
  val suggestedName = suggestedSdkName ?: suggestAssociatedSdkName(homeFile.path, associatedProjectPath)
  return SdkConfigurationUtil.setupSdk(existingSdks.toTypedArray(), homeFile,
                                       PythonSdkType.getInstance(),
                                       false, null, suggestedName)
}

fun showSdkExecutionException(sdk: Sdk?, e: ExecutionException, @NlsContexts.DialogTitle title: String) {
  runInEdt {
    val description = PyPackageManagementService.toErrorDescription(listOf(e), sdk) ?: return@runInEdt
    PackagesNotificationPanel.showError(title, description)
  }
}

fun Sdk.associateWithModule(module: Module?, newProjectPath: String?) {
  getOrCreateAdditionalData().apply {
    when {
      newProjectPath != null -> associateWithModulePath(newProjectPath)
      module != null -> associateWithModule(module)
    }
  }
}

fun Sdk.isAssociatedWithModule(module: Module?): Boolean {
  val basePath = module?.basePath
  val associatedPath = associatedModulePath
  if (basePath != null && associatedPath == basePath) return true
  if (isAssociatedWithAnotherModule(module)) return false
  return isLocatedInsideModule(module) || containsModuleName(module)
}

fun Sdk.isAssociatedWithAnotherModule(module: Module?): Boolean {
  val basePath = module?.basePath ?: return false
  val associatedPath = associatedModulePath ?: return false
  return basePath != associatedPath
}

val Sdk.associatedModulePath: String?
  // TODO: Support .project associations
  get() = associatedPathFromAdditionalData /*?: associatedPathFromDotProject*/

val Sdk.associatedModuleDir: VirtualFile?
  get() = associatedModulePath?.let { StandardFileSystems.local().findFileByPath(it) }

fun Sdk.adminPermissionsNeeded(): Boolean {
  val pathToCheck = sitePackagesDirectory?.path ?: homePath ?: return false
  return !Files.isWritable(Paths.get(pathToCheck))
}

fun PyDetectedSdk.setup(existingSdks: List<Sdk>): Sdk? {
  val homeDir = homeDirectory ?: return null
  return SdkConfigurationUtil.setupSdk(existingSdks.toTypedArray(), homeDir, PythonSdkType.getInstance(), false, null, null)
}

fun PyDetectedSdk.setupTargetAware(existingSdks: List<Sdk>, targetEnvironmentConfiguration: TargetEnvironmentConfiguration?): Sdk? {
  val homeDir = homeDirectory ?: return null
  val sdk = SdkConfigurationUtil.createSdk(existingSdks, homeDir, PythonSdkType.getInstance(), null, null)
  sdk.sdkAdditionalData = PyTargetAwareAdditionalData(flavor = null)
    .also {
      it.targetEnvironmentConfiguration = targetEnvironmentConfiguration
    }
  PythonSdkType.getInstance().setupSdkPaths(sdk)
  return sdk
}

fun PyDetectedSdk.setupAssociated(existingSdks: List<Sdk>, associatedModulePath: String?): Sdk? {
  val homeDir = homeDirectory ?: return null
  val suggestedName = homePath?.let { suggestAssociatedSdkName(it, associatedModulePath) }
  return SdkConfigurationUtil.setupSdk(existingSdks.toTypedArray(), homeDir, PythonSdkType.getInstance(), false, null, suggestedName)
}

var Module.pythonSdk: Sdk?
  get() = PythonSdkUtil.findPythonSdk(this)
  set(value) {
    ModuleRootModificationUtil.setModuleSdk(this, value)
    PyUiUtil.clearFileLevelInspectionResults(project)
  }

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

fun Module.excludeInnerVirtualEnv(sdk: Sdk) {
  val root = getInnerVirtualEnvRoot(sdk) ?: return

  val model = ModuleRootManager.getInstance(this).modifiableModel

  val contentEntry = model.contentEntries.firstOrNull {
    val contentFile = it.file
    contentFile != null && VfsUtil.isAncestor(contentFile, root, true)
  } ?: return
  contentEntry.addExcludeFolder(root)

  WriteAction.run<Throwable> {
    model.commit()
  }
}

fun Project?.excludeInnerVirtualEnv(sdk: Sdk) {
  val binary = sdk.homeDirectory ?: return
  val possibleProjects = if (this != null) listOf(this) else ProjectManager.getInstance().openProjects.asList()
  possibleProjects.firstNotNullOfOrNull { ModuleUtil.findModuleForFile(binary, it) }?.excludeInnerVirtualEnv(sdk)
}

fun getInnerVirtualEnvRoot(sdk: Sdk): VirtualFile? {
  val binaryPath = sdk.homePath ?: return null

  val possibleVirtualEnv = PythonSdkUtil.getVirtualEnvRoot(binaryPath)

  return if (possibleVirtualEnv != null) {
    LocalFileSystem.getInstance().findFileByIoFile(possibleVirtualEnv)
  }
  else if (PythonSdkUtil.isCondaVirtualEnv(binaryPath)) {
    PythonSdkUtil.getCondaDirectory(sdk)
  }
  else {
    null
  }
}

private fun suggestAssociatedSdkName(sdkHome: String, associatedPath: String?): String? {
  // please don't forget to update com.jetbrains.python.inspections.PyInterpreterInspection.Visitor#getSuitableSdkFix
  // after changing this method

  val baseSdkName = PythonSdkType.suggestBaseSdkName(sdkHome) ?: return null
  val venvRoot = PythonSdkUtil.getVirtualEnvRoot(sdkHome)?.path
  val condaRoot = CondaEnvSdkFlavor.getCondaEnvRoot(sdkHome)?.path
  val associatedName = when {
    venvRoot != null && (associatedPath == null || !FileUtil.isAncestor(associatedPath, venvRoot, true)) ->
      PathUtil.getFileName(venvRoot)
    condaRoot != null && (associatedPath == null || !FileUtil.isAncestor(associatedPath, condaRoot, true)) ->
      PathUtil.getFileName(condaRoot)
    PythonSdkUtil.isBaseConda(sdkHome) ->
      "base"
    else ->
      associatedPath?.let { PathUtil.getFileName(associatedPath) } ?: return null
  }
  return "$baseSdkName ($associatedName)"
}

val File.isNotEmptyDirectory: Boolean
  get() = exists() && isDirectory && list()?.isEmpty()?.not() ?: false

private val Sdk.isSystemWide: Boolean
  get() = !PythonSdkUtil.isRemote(this) && !PythonSdkUtil.isVirtualEnv(
    this) && !PythonSdkUtil.isCondaVirtualEnv(this)

@Suppress("unused")
private val Sdk.associatedPathFromDotProject: String?
  get() {
    val binaryPath = homePath ?: return null
    val virtualEnvRoot = PythonSdkUtil.getVirtualEnvRoot(binaryPath) ?: return null
    val projectFile = File(virtualEnvRoot, ".project")
    return try {
      projectFile.readText().trim()
    }
    catch (e: IOException) {
      null
    }
  }

private val Sdk.associatedPathFromAdditionalData: String?
  get() = (sdkAdditionalData as? PythonSdkAdditionalData)?.associatedModulePath

private val Sdk.sitePackagesDirectory: VirtualFile?
  get() = PythonSdkUtil.getSitePackagesDirectory(this)

val Sdk.sdkFlavor: PythonSdkFlavor?
  get() {
    val remoteSdkData = remoteSdkAdditionalData
    if (remoteSdkData != null) {
      return remoteSdkData.flavor
    }
    return PythonSdkFlavor.getFlavor(this)
  }

val Sdk.remoteSdkAdditionalData: PyRemoteSdkAdditionalDataBase?
  get() = sdkAdditionalData as? PyRemoteSdkAdditionalDataBase

fun Sdk.isLocatedInsideModule(module: Module?): Boolean {
  val baseDirPath = try {
    module?.baseDir?.toNioPath()
  } catch (e: UnsupportedOperationException) {
    return false
  }
  return isLocatedInsideBaseDir(baseDirPath)
}

private fun Sdk.isLocatedInsideBaseDir(baseDir: Path?): Boolean {
  val homePath = homePath ?: return false
  val basePath = baseDir?.toString() ?: return false
  return FileUtil.isAncestor(basePath, homePath, true)
}

val PyDetectedSdk.guessedLanguageLevel: LanguageLevel?
  get() {
    val path = homePath ?: return null
    val result = Regex(""".*python(\d\.\d)""").find(path) ?: return null
    val versionString = result.groupValues.getOrNull(1) ?: return null
    return LanguageLevel.fromPythonVersion(versionString)
  }

private fun Sdk.containsModuleName(module: Module?): Boolean {
  val path = homePath ?: return false
  val name = module?.name ?: return false
  return path.contains(name, true)
}

fun Sdk.getOrCreateAdditionalData(): PythonSdkAdditionalData {
  val existingData = sdkAdditionalData as? PythonSdkAdditionalData
  if (existingData != null) return existingData
  val newData = PythonSdkAdditionalData(PythonSdkFlavor.getFlavor(homePath))
  val modificator = sdkModificator
  modificator.sdkAdditionalData = newData
  ApplicationManager.getApplication().runWriteAction { modificator.commitChanges() }
  return newData
}

private fun filterSuggestedPaths(suggestedPaths: Collection<String>,
                                 existingSdks: List<Sdk>,
                                 module: Module?,
                                 context: UserDataHolder,
                                 mayContainCondaEnvs: Boolean = false): List<PyDetectedSdk> {
  val existingPaths = existingSdks.mapTo(HashSet()) { it.homePath }
  val baseDirFromContext = context.getUserData(BASE_DIR)
  return suggestedPaths
    .asSequence()
    .filterNot { it in existingPaths }
    .distinct()
    .map { PyDetectedSdk(it) }
    .sortedWith(
      compareBy(
        { !it.isAssociatedWithModule(module) && !it.isLocatedInsideBaseDir(baseDirFromContext) },
        { if (mayContainCondaEnvs) !PythonSdkUtil.isBaseConda(it.homePath) else false },
        { it.homePath }
      )
    )
    .toList()
}

fun Sdk?.isTargetBased(): Boolean = this != null && sdkAdditionalData is PyTargetAwareAdditionalData