// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.execution.ExecutionException
import com.intellij.execution.target.*
import com.intellij.ide.projectView.actions.MarkRootsManager
import com.intellij.openapi.application.*
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.*
import com.intellij.openapi.vfs.ex.temp.TempFileSystem
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.util.PathUtil
import com.intellij.webcore.packaging.PackagesNotificationPanel
import com.jetbrains.python.PyBundle
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.isCondaVirtualEnv
import com.jetbrains.python.isVirtualEnv
import com.jetbrains.python.packaging.ui.PyPackageManagementService
import com.jetbrains.python.packaging.utils.PyPackageCoroutine
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.remote.PyRemoteSdkAdditionalData
import com.jetbrains.python.run.PythonInterpreterTargetEnvironmentFactory
import com.jetbrains.python.sdk.configuration.PyProjectSdkConfiguration.setReadyToUseSdk
import com.jetbrains.python.sdk.flavors.PyFlavorAndData
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor
import com.jetbrains.python.sdk.flavors.VirtualEnvSdkFlavor
import com.jetbrains.python.sdk.flavors.conda.CondaEnvSdkFlavor
import com.jetbrains.python.sdk.readOnly.PythonSdkReadOnlyProvider
import com.jetbrains.python.target.PyTargetAwareAdditionalData
import com.jetbrains.python.target.createDetectedSdk
import com.jetbrains.python.util.ShowingMessageErrorSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus.Internal
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import javax.swing.SwingUtilities
import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.io.path.pathString

internal data class TargetAndPath(
  val target: TargetEnvironmentConfiguration?,
  val path: FullPathOnTarget?,
)

@get:Internal
val BASE_DIR: Key<Path> = Key.create("PYTHON_PROJECT_BASE_PATH")

@Internal
fun findAllPythonSdks(baseDir: Path?): List<Sdk> {
  val context: UserDataHolder = UserDataHolderBase()
  if (baseDir != null) {
    context.putUserData(BASE_DIR, baseDir)
  }
  val existing = PythonSdkUtil.getAllSdks()
  return detectVirtualEnvs(null, existing, context) + findBaseSdks(existing, null, context)
}

@Internal
fun findBaseSdks(existingSdks: List<Sdk>, module: Module?, context: UserDataHolder): List<Sdk> {
  val existing = filterSystemWideSdks(existingSdks)
    .sortedWith(PreferredSdkComparator.INSTANCE)
    .filterNot { PythonSdkUtil.isBaseConda(it.homePath) }

  val detected = detectSystemWideSdks(module, existingSdks, context).filterNot { PythonSdkUtil.isBaseConda(it.homePath) }
  return existing + detected
}

fun mostPreferred(sdks: List<Sdk>): Sdk? = sdks.minWithOrNull(PreferredSdkComparator.INSTANCE)

@Internal
fun filterSystemWideSdks(existingSdks: List<Sdk>): List<Sdk> {
  return existingSdks.filter { it.sdkType is PythonSdkType && it.isSystemWide }
}

@Internal
fun configurePythonSdk(project: Project, module: Module, sdk: Sdk) {
  // in case module contains root of the project we consider it as a project wide interpreter
  if (project.basePath == module.basePath) {
    project.pythonSdk = sdk
  }

  module.pythonSdk = sdk
  module.excludeInnerVirtualEnv(sdk)
}

// TODO: PythonInterpreterService: get system pythons
/**
 * @param context used to get [BASE_DIR] in [VirtualEnvSdkFlavor.suggestLocalHomePaths]
 */
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

private fun PythonSdkFlavor<*>.detectSdks(
  module: Module?,
  context: UserDataHolder,
  targetModuleSitsOn: TargetConfigurationWithLocalFsAccess?,
  existingPaths: HashSet<TargetAndPath>,
): List<PyDetectedSdk> =
  detectSdkPaths(module, context, targetModuleSitsOn, existingPaths)
    .map { createDetectedSdk(it, targetModuleSitsOn?.asTargetConfig, this) }


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
fun detectVirtualEnvs(module: Module?, existingSdks: List<Sdk>, context: UserDataHolder): List<PyDetectedSdk> =
  filterSuggestedPaths(VirtualEnvSdkFlavor.getInstance(), existingSdks, module, context)

@Internal
fun filterSharedCondaEnvs(module: Module?, existingSdks: List<Sdk>): List<Sdk> {
  return existingSdks.filter { PythonSdkUtil.isPythonSdk(it) && it.isCondaVirtualEnv && !it.isAssociatedWithAnotherModule(module) }
}

@Internal
fun filterAssociatedSdks(module: Module, existingSdks: List<Sdk>): List<Sdk> {
  return existingSdks.filter { PythonSdkUtil.isPythonSdk(it) && it.isAssociatedWithModule(module) }
}

@Internal
fun detectAssociatedEnvironments(module: Module, existingSdks: List<Sdk>, context: UserDataHolder): List<PyDetectedSdk> =
  detectVirtualEnvs(module, existingSdks, context).filter { it.isAssociatedWithModule(module) }

@Deprecated("Please use version with sdkAdditionalData parameter")
fun createSdkByGenerateTask(
  generateSdkHomePath: Task.WithResult<String, ExecutionException>,
  existingSdks: List<Sdk>,
  baseSdk: Sdk?,
  associatedProjectPath: String?,
  suggestedSdkName: String?,
): Sdk = createSdkByGenerateTask(generateSdkHomePath, existingSdks, baseSdk, associatedProjectPath, suggestedSdkName, null)

@Internal
fun createSdkByGenerateTask(
  generateSdkHomePath: Task.WithResult<String, ExecutionException>,
  existingSdks: List<Sdk>,
  baseSdk: Sdk?,
  associatedProjectPath: String?,
  suggestedSdkName: String?,
  sdkAdditionalData: PythonSdkAdditionalData? = null,
): Sdk {
  val homeFile = try {
    val homePath = ProgressManager.getInstance().run(generateSdkHomePath)
    StandardFileSystems.local().refreshAndFindFileByPath(homePath) ?: throw ExecutionException(
      PyBundle.message("python.sdk.directory.not.found", homePath)
    )
  }
  catch (e: ExecutionException) {
    showSdkExecutionException(baseSdk, e, PyBundle.message("python.sdk.failed.to.create.interpreter.title"))
    throw e
  }

  val sdkName = suggestedSdkName ?: if (SwingUtilities.isEventDispatchThread()) {
    runWithModalProgressBlocking(ModalTaskOwner.guess(), "...") {
      withContext(Dispatchers.IO) {
        suggestAssociatedSdkName(homeFile.path, associatedProjectPath)
      }
    }
  }
  else {
    runBlockingMaybeCancellable {
      suggestAssociatedSdkName(homeFile.path, associatedProjectPath)
    }
  }
  return SdkConfigurationUtil.setupSdk(
    existingSdks.toTypedArray(),
    homeFile,
    PythonSdkType.getInstance(),
    sdkAdditionalData,
    sdkName)
}

@Internal
suspend fun createSdk(
  sdkHomePath: Path,
  existingSdks: List<Sdk>,
  associatedProjectPath: String?,
  suggestedSdkName: String?,
  sdkAdditionalData: PythonSdkAdditionalData? = null,
): PyResult<Sdk> {
  val homeFile = withContext(Dispatchers.IO) { StandardFileSystems.local().refreshAndFindFileByPath(sdkHomePath.pathString) }
                 ?: return PyResult.localizedError(PyBundle.message("python.sdk.directory.not.found", sdkHomePath.pathString))

  val sdkName = suggestedSdkName ?: suggestAssociatedSdkName(homeFile.path, associatedProjectPath)
  val sdk = SdkConfigurationUtil.setupSdk(
    existingSdks.toTypedArray(),
    homeFile,
    PythonSdkType.getInstance(),
    false,
    sdkAdditionalData,
    sdkName)

  return sdk?.let { PyResult.success(it) }
         ?: PyResult.localizedError(PyBundle.message("python.sdk.failed.to.create.interpreter.title"))
}

internal fun showSdkExecutionException(sdk: Sdk?, e: ExecutionException, @NlsContexts.DialogTitle title: String) {
  runInEdt {
    val description = PyPackageManagementService.toErrorDescription(listOf(e), sdk) ?: return@runInEdt
    PackagesNotificationPanel.showError(title, description)
  }
}

@Internal
fun Sdk.isAssociatedWithModule(module: Module?): Boolean {
  val basePath = module?.basePath
  val associatedPath = associatedModulePath
  if (basePath != null && associatedPath == basePath) return true
  if (isAssociatedWithAnotherModule(module)) return false
  return isLocatedInsideModule(module) || containsModuleName(module)
}

@Internal
fun Sdk.isAssociatedWithAnotherModule(module: Module?): Boolean {
  val basePath = module?.basePath ?: return false
  val associatedPath = associatedModulePath ?: return false
  return basePath != associatedPath
}

@get:Internal
val Sdk.associatedModulePath: String?
  // TODO: Support .project associations
  get() = associatedPathFromAdditionalData /*?: associatedPathFromDotProject*/


@get:Internal
val Sdk.associatedModuleNioPath: Path?
  get() = runCatching {
    associatedModulePath?.let { Path(it) }
  }.getOrNull()

internal val Sdk.associatedModuleDir: VirtualFile?
  get() {
    val nioPath = associatedModuleNioPath ?: return null
    return VirtualFileManager.getInstance().findFileByNioPath(nioPath) ?: TempFileSystem.getInstance().findFileByNioFile(nioPath)
  }

internal fun Sdk.adminPermissionsNeeded(): Boolean {
  val pathToCheck = sitePackagesDirectory?.path ?: homePath ?: return false
  return !Files.isWritable(Paths.get(pathToCheck))
}

@Internal
fun PyDetectedSdk.setup(existingSdks: List<Sdk>): Sdk? {
  val homeDir = homeDirectory ?: return null
  return SdkConfigurationUtil.setupSdk(existingSdks.toTypedArray(), homeDir, PythonSdkType.getInstance(), null, null)
}

// For Java only
internal fun PyDetectedSdk.setupSdkLaunch(
  module: Module,
  existingSdks: List<Sdk>,
  doAssociate: Boolean,
) {
  PyPackageCoroutine.launch(project = module.project) {
    setupSdk(module, existingSdks, doAssociate)
  }
}

@Internal
suspend fun PyDetectedSdk.setupSdk(
  module: Module,
  existingSdks: List<Sdk>,
  doAssociate: Boolean,
) {
  val newSdk = setupAssociated(existingSdks, module.basePath, doAssociate).getOr {
    ShowingMessageErrorSync.emit(it.error)
    return
  }
  withContext(Dispatchers.EDT) {
    SdkConfigurationUtil.addSdk(newSdk)
  }
  setReadyToUseSdk(module.project, module, newSdk)
}

@Internal
suspend fun PyDetectedSdk.setupAssociated(
  existingSdks: List<Sdk>,
  associatedModulePath: String?,
  doAssociate: Boolean,
): PyResult<Sdk> = withContext(Dispatchers.IO) {
  if (!sdkSeemsValid) {
    return@withContext PyResult.localizedError(PyBundle.message("python.sdk.error.invalid.interpreter.selected", homePath))
  }

  val homePath = homePath
  if (homePath == null) {
    // e.g. directory is not there anymore
    return@withContext PyResult.localizedError(PyBundle.message("python.sdk.provided.path.is.invalid", null))
  }

  val homeDir = homeDirectory
  if (homeDir == null) {
    return@withContext PyResult.localizedError(PyBundle.message("python.sdk.provided.path.is.invalid", null))
  }

  val suggestedName = if (doAssociate) {
    suggestAssociatedSdkName(homePath, associatedModulePath)
  }
  else null

  val data = targetEnvConfiguration?.let { targetConfig ->
    PyTargetAwareAdditionalData(PyFlavorAndData.UNKNOWN_FLAVOR_DATA).also {
      it.targetEnvironmentConfiguration = targetConfig
    }
  } ?: PythonSdkAdditionalData()

  if (doAssociate && associatedModulePath != null) {
    data.associatedModulePath = associatedModulePath
  }

  val sdk = withContext(Dispatchers.EDT) {
    SdkConfigurationUtil.setupSdk(
      existingSdks.toTypedArray(),
      homeDir,
      PythonSdkType.getInstance(),
      data,
      suggestedName)

  }
  PyResult.success(sdk)
}

var Module.pythonSdk: Sdk?
  get() = PythonSdkUtil.findPythonSdk(this)
  set(newSdk) {
    val prevSdk = pythonSdk
    thisLogger().info("Setting PythonSDK $newSdk to module $this")
    ModuleRootModificationUtil.setModuleSdk(this, newSdk)
    runInEdt {
      DaemonCodeAnalyzer.getInstance(project).restart()
    }
    ApplicationManager.getApplication().messageBus.syncPublisher(PySdkListener.TOPIC).moduleSdkUpdated(this, prevSdk, newSdk)
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

@Internal
fun Module.excludeInnerVirtualEnv(sdk: Sdk) {
  val root = getInnerVirtualEnvRoot(sdk) ?: return

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

internal suspend fun suggestAssociatedSdkName(sdkHome: String, associatedPath: String?): String? = withContext(Dispatchers.IO) {
  // please don't forget to update com.jetbrains.python.inspections.PyInterpreterInspection.Visitor#getSuitableSdkFix
  // after changing this method

  val baseSdkName = PythonSdkType.suggestBaseSdkName(sdkHome) ?: return@withContext null
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
      associatedPath?.let { PathUtil.getFileName(associatedPath) } ?: return@withContext null
  }
  return@withContext "$baseSdkName ($associatedName)"
}

internal val Sdk.isSystemWide: Boolean
  get() = !PythonSdkUtil.isRemote(this) && !this.isVirtualEnv && !this.isCondaVirtualEnv


@get:Internal
val Sdk.isReadOnly: Boolean
  get() = PythonSdkReadOnlyProvider.isReadOnly(this)

@get:Internal
val Sdk.readOnlyErrorMessage: String
  get() = PythonSdkReadOnlyProvider.getReadOnlyMessage(this) ?: PyBundle.message("python.sdk.read.only", name)

private val Sdk.associatedPathFromAdditionalData: String?
  get() = (sdkAdditionalData as? PythonSdkAdditionalData)?.associatedModulePath

private val Sdk.sitePackagesDirectory: VirtualFile?
  get() = PythonSdkUtil.getSitePackagesDirectory(this)

val Sdk.sdkFlavor: PythonSdkFlavor<*> get() = getOrCreateAdditionalData().flavor

@Internal
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

@get:Internal
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


@JvmName("getOrCreateAdditionalData")
fun getOrCreateAdditionalDataOld(sdk: Sdk): PythonSdkAdditionalData = sdk.getOrCreateAdditionalData()

private fun filterSuggestedPaths(
  flavor: PythonSdkFlavor<*>,
  existingSdks: List<Sdk>,
  module: Module?,
  context: UserDataHolder,
  mayContainCondaEnvs: Boolean = false,
): List<PyDetectedSdk> {
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

/**
 * Where a "remote_sources" folder for certain SDK is stored
 */
@get:Internal
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
 * Configures [targetCommandLineBuilder] (sets a binary path and other stuff) so it could run python on this target
 */
@Internal
fun Sdk.configureBuilderToRunPythonOnTarget(targetCommandLineBuilder: TargetedCommandLineBuilder) {
  getOrCreateAdditionalData().flavorAndData.data.prepareTargetCommandLine(this, targetCommandLineBuilder)
}

/**
 * Returns whether this [Sdk] seems valid or not.
 *
 * The actual check logic is located in [PythonSdkFlavor.sdkSeemsValid] and its overrides. In general, the method check whether the path to
 * the Python binary stored in this [Sdk] exists and the corresponding file can be executed. This check can be performed both locally and
 * on a target. The latter case takes place when [PythonSdkAdditionalData] of this [Sdk] implements [PyTargetAwareAdditionalData] and the
 * corresponding target provides file system operations (see [com.jetbrains.python.pathValidation.ValidationRequest]).
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
    return pythonSdkAdditionalData.flavorAndData.sdkSeemsValid(this, targetEnvConfiguration)
  }