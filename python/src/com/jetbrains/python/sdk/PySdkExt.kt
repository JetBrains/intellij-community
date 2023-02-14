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
import com.intellij.execution.target.FullPathOnTarget
import com.intellij.execution.target.TargetConfigurationWithLocalFsAccess
import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.execution.target.TargetedCommandLineBuilder
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.progress.EmptyProgressIndicator
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
import com.jetbrains.python.remote.PyRemoteSdkAdditionalData
import com.jetbrains.python.remote.PyRemoteSdkAdditionalDataBase
import com.jetbrains.python.run.PythonInterpreterTargetEnvironmentFactory
import com.jetbrains.python.sdk.add.target.PyDetectedSdkAdditionalData
import com.jetbrains.python.sdk.add.target.createDetectedSdk
import com.jetbrains.python.sdk.flavors.PyFlavorAndData
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor
import com.jetbrains.python.sdk.flavors.VirtualEnvSdkFlavor
import com.jetbrains.python.sdk.flavors.conda.CondaEnvSdkFlavor
import com.jetbrains.python.target.PyTargetAwareAdditionalData
import com.jetbrains.python.ui.PyUiUtil
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.div
import kotlin.io.path.pathString

private data class TargetAndPath(val target: TargetEnvironmentConfiguration?,
                                 val path: FullPathOnTarget?)


val BASE_DIR: Key<Path> = Key.create("PYTHON_PROJECT_BASE_PATH")

fun findAllPythonSdks(baseDir: Path?): List<Sdk> {
  val context: UserDataHolder = UserDataHolderBase()
  if (baseDir != null) {
    context.putUserData(BASE_DIR, baseDir)
  }
  val existing = PythonSdkUtil.getAllSdks()
  return detectVirtualEnvs(null, existing, context) + findBaseSdks(existing, null, context)
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
fun detectSystemWideSdks(module: Module?,
                         existingSdks: List<Sdk>,
                         context: UserDataHolder = UserDataHolderBase()): List<PyDetectedSdk> {
  if (module != null && module.isDisposed) return emptyList()
  val targetModuleSitsOn = module?.let { PythonInterpreterTargetEnvironmentFactory.getTargetModuleResidesOn(it) }
  val existingPaths = existingSdks.mapTo(HashSet()) { TargetAndPath(it.targetEnvConfiguration, it.homePath) }
  return PythonSdkFlavor.getApplicableFlavors(false)
    .flatMap { flavor -> flavor.detectSdks(module, context, targetModuleSitsOn, existingPaths) }
    .sortedWith(compareBy<PyDetectedSdk>({ it.guessedLanguageLevel },
                                         { it.homePath }).reversed())
}

private fun PythonSdkFlavor<*>.detectSdks(module: Module?,
                                          context: UserDataHolder,
                                          targetModuleSitsOn: TargetConfigurationWithLocalFsAccess?,
                                          existingPaths: HashSet<TargetAndPath>): List<PyDetectedSdk> =
  suggestLocalHomePaths(module, context)
    .mapNotNull {
      // If module sits on target, this target maps its path.
      if (targetModuleSitsOn == null) it.pathString else targetModuleSitsOn.getTargetPathIfLocalPathIsOnTarget(it)
    }
    .filter { TargetAndPath(targetModuleSitsOn?.asTargetConfig, it) !in existingPaths }
    .map { createDetectedSdk(it, targetModuleSitsOn?.asTargetConfig, this) }

fun resetSystemWideSdksDetectors() {
  PythonSdkFlavor.getApplicableFlavors(false).forEach(PythonSdkFlavor<*>::dropCaches)
}

fun detectVirtualEnvs(module: Module?, existingSdks: List<Sdk>, context: UserDataHolder): List<PyDetectedSdk> =
  filterSuggestedPaths(VirtualEnvSdkFlavor.getInstance(), existingSdks, module, context)

fun filterSharedCondaEnvs(module: Module?, existingSdks: List<Sdk>): List<Sdk> {
  return existingSdks.filter { it.sdkType is PythonSdkType && PythonSdkUtil.isConda(it) && !it.isAssociatedWithAnotherModule(module) }
}

fun filterAssociatedSdks(module: Module, existingSdks: List<Sdk>): List<Sdk> {
  return existingSdks.filter { it.sdkType is PythonSdkType && it.isAssociatedWithModule(module) }
}

fun detectAssociatedEnvironments(module: Module, existingSdks: List<Sdk>, context: UserDataHolder): List<PyDetectedSdk> =
  detectVirtualEnvs(module, existingSdks, context).filter { it.isAssociatedWithModule(module) }

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


fun PyDetectedSdk.setupAssociated(existingSdks: List<Sdk>, associatedModulePath: String?): Sdk? {
  if (!sdkSeemsValid) return null
  val homePath = this.homePath ?: return null
  val suggestedName = suggestAssociatedSdkName(homePath, associatedModulePath) ?: homePath
  val sdk = SdkConfigurationUtil.createSdk(existingSdks, homePath, PythonSdkType.getInstance(), null, suggestedName)

  val targetConfig = (sdkAdditionalData as? PyDetectedSdkAdditionalData)?.temporaryConfiguration
  if (targetConfig != null) { // Target-based sdk, not local one
    sdk.sdkAdditionalData = PyTargetAwareAdditionalData(PyFlavorAndData.UNKNOWN_FLAVOR_DATA)
      .also {
        it.targetEnvironmentConfiguration = targetConfig
      }
  }
  PythonSdkType.getInstance().setupSdkPaths(sdk)
  return sdk
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

val Sdk.sdkFlavor: PythonSdkFlavor<*> get() = getOrCreateAdditionalData().flavor

val Sdk.remoteSdkAdditionalData: PyRemoteSdkAdditionalDataBase?
  get() = sdkAdditionalData as? PyRemoteSdkAdditionalDataBase

fun Sdk.isLocatedInsideModule(module: Module?): Boolean {
  val moduleDir = module?.baseDir
  val sdkDir = homeDirectory
  return moduleDir != null && sdkDir != null && VfsUtil.isAncestor(moduleDir, sdkDir, true)
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

/**
 * Each [Sdk] has [PythonSdkAdditionalData]. Use this method to get it.
 * Although each SDK should already have one, some old may lack of it.
 *
 * This method creates new in this case, but only if SDK flavor doesn't require special additional data.
 */
fun Sdk.getOrCreateAdditionalData(): PythonSdkAdditionalData {
  val existingData = sdkAdditionalData as? PythonSdkAdditionalData
  if (existingData != null) return existingData
  val flavor = PythonSdkFlavor.getFlavor(homePath)
  val newData = PythonSdkAdditionalData(flavor?.let { if (it.supportsEmptyData()) it else null })
  val modificator = sdkModificator
  modificator.sdkAdditionalData = newData
  ApplicationManager.getApplication().let {
    it.invokeLater {
      it.runWriteAction {
        modificator.commitChanges()
      }
    }
  }
  return newData
}


private fun filterSuggestedPaths(flavor: PythonSdkFlavor<*>,
                                 existingSdks: List<Sdk>,
                                 module: Module?,
                                 context: UserDataHolder,
                                 mayContainCondaEnvs: Boolean = false): List<PyDetectedSdk> {
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
    .map { createDetectedSdk(it, targetModuleSitsOn?.asTargetConfig, flavor) }
    .sortedWith(
      compareBy(
        { !it.isAssociatedWithModule(module) && !it.isLocatedInsideBaseDir(baseDirFromContext) },
        { if (mayContainCondaEnvs) !PythonSdkUtil.isBaseConda(it.homePath) else false },
        { it.homePath }
      )
    )
    .toList()
}

fun Sdk?.isTargetBased(): Boolean = this != null && targetEnvConfiguration != null

/**
 *  Additional data if sdk is target-based
 */
val Sdk.targetAdditionalData get():PyTargetAwareAdditionalData? = sdkAdditionalData as? PyTargetAwareAdditionalData

/**
 * Returns target environment if configuration is target api based
 */
val Sdk.targetEnvConfiguration get():TargetEnvironmentConfiguration? = targetAdditionalData?.targetEnvironmentConfiguration

/**
 * Where "remote_sources" folder for certain SDK is stored
 */
val Sdk.remoteSourcesLocalPath: Path
  get() =
    Path.of(PathManager.getSystemPath()) /
    Path.of(PythonSdkUtil.REMOTE_SOURCES_DIR_NAME) /
    Path.of(when (val data = sdkAdditionalData) {
              is PyTargetAwareAdditionalData -> data.uuid.toString()
              is PyRemoteSdkAdditionalData -> homePath!!
              else -> error("Only legacy and remote SDK and target-based SDKs are supported")
            }.hashCode().toString())


/**
 * Configures [targetCommandLineBuilder] (sets binary path and other stuff) so it could run python on this target
 */
fun Sdk.configureBuilderToRunPythonOnTarget(targetCommandLineBuilder: TargetedCommandLineBuilder) {
  getOrCreateAdditionalData().flavorAndData.data.prepareTargetCommandLine(this, targetCommandLineBuilder)
}

/**
 * Returns whether this [Sdk] seems valid or not.
 *
 * The actual check logic is located in [PythonSdkFlavor.sdkSeemsValid] and its overrides. In general, the method check whether the path to
 * the Python binary stored in this [Sdk] exists and the corresponding file can be executed. This check can be performed both locally and
 * on a target. The latter case takes place when [PythonSdkAdditionalData] of this [Sdk] implements [PyTargetAwareAdditionalData] and the
 * corresponding target provides file system operations (by implementing the interface
 * [com.intellij.execution.target.readableFs.TargetConfigurationReadableFs]).
 *
 * Note that if [PythonSdkAdditionalData] of this [Sdk] is [PyRemoteSdkAdditionalData] this method does not do any checks and returns
 * `true`. This behavior may be improved in the future by generating [TargetEnvironmentConfiguration] based on the present
 * [PyRemoteSdkAdditionalData].
 *
 * @see PythonSdkFlavor.sdkSeemsValid
 */
val Sdk.sdkSeemsValid: Boolean
  get() {
    val pythonSdkAdditionalData = getOrCreateAdditionalData()
    if (pythonSdkAdditionalData is PyRemoteSdkAdditionalData) return true
    return pythonSdkAdditionalData.flavorAndData
      .sdkSeemsValid(this, (pythonSdkAdditionalData as? PyDetectedSdkAdditionalData)?.temporaryConfiguration ?: targetEnvConfiguration)
  }

private val SDK_PYTHON_PATH = Key<FullPathOnTarget>("SDK_PYTHON_PATH")

/**
 * @return path to python binary on target
 */
suspend fun Sdk.getPythonBinaryPath(project: Project): Result<FullPathOnTarget> {
  val cachedPath = getUserData(SDK_PYTHON_PATH)
  if (cachedPath != null) return Result.success(cachedPath)
  val executionResult = PyTargetsIntrospectionFacade(this, project)
    .getCommandOutput(EmptyProgressIndicator(), "import sys; print(sys.executable)").map { it.trim() }
  val path = executionResult.getOrNull()
  if (path != null) {
    putUserData(SDK_PYTHON_PATH, path)
  }
  return executionResult
}