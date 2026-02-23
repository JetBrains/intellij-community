// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk

import com.intellij.execution.ExecutionException
import com.intellij.execution.target.FullPathOnTarget
import com.intellij.execution.target.TargetBasedSdkAdditionalData
import com.intellij.execution.target.TargetConfigurationWithLocalFsAccess
import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.execution.target.TargetedCommandLineBuilder
import com.intellij.ide.projectView.actions.MarkRootsManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
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
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.ex.temp.TempFileSystem
import com.intellij.openapi.vfs.refreshAndFindVirtualFile
import com.intellij.platform.eel.EelApi
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.python.community.services.systemPython.SystemPythonService
import com.intellij.util.PathUtil
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.ui.EDT
import com.intellij.webcore.packaging.PackagesNotificationPanel
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.errorProcessing.emit
import com.jetbrains.python.isCondaVirtualEnv
import com.jetbrains.python.isVirtualEnv
import com.jetbrains.python.packaging.ui.PyPackageManagementService
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.run.PythonInterpreterTargetEnvironmentFactory
import com.jetbrains.python.sdk.add.v2.PathHolder
import com.jetbrains.python.sdk.configuration.PyProjectSdkConfiguration.setReadyToUseSdk
import com.jetbrains.python.sdk.flavors.PyFlavorAndData
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor
import com.jetbrains.python.sdk.flavors.VirtualEnvSdkFlavor
import com.jetbrains.python.sdk.legacy.PythonSdkUtil
import com.jetbrains.python.sdk.legacy.PythonSdkUtil.isPythonSdk
import com.jetbrains.python.sdk.readOnly.PythonSdkReadOnlyProvider
import com.jetbrains.python.target.PyTargetAwareAdditionalData
import com.jetbrains.python.target.createDetectedSdk
import com.jetbrains.python.util.ShowingMessageErrorSync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Internal
import java.nio.file.InvalidPathException
import java.nio.file.Path
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
 * @param context used to get [BASE_DIR] in [VirtualEnvSdkFlavor.suggestLocalHomePaths]
 */
@ApiStatus.Obsolete
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
fun filterAssociatedSdks(module: Module, existingSdks: List<Sdk>): List<Sdk> {
  return existingSdks.filter { isPythonSdk(it) && it.isAssociatedWithModule(module) }
}

@Internal
@RequiresBackgroundThread
fun detectAssociatedEnvironments(module: Module, existingSdks: List<Sdk>, context: UserDataHolder): List<PyDetectedSdk> =
  detectVirtualEnvs(module, existingSdks, context).filter { it.isAssociatedWithModule(module) }

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
      PyBundle.message("python.sdk.python.executable.not.found", homePath)
    )
  }
  catch (e: ExecutionException) {
    showSdkExecutionException(baseSdk, e, PyBundle.message("python.sdk.failed.to.create.interpreter.title"))
    throw e
  }

  val sdkName = suggestedSdkName ?: if (EDT.isCurrentThreadEdt()) {
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
  pythonBinaryPath: PathHolder.Eel,
  associatedModulePath: String,
  suggestedSdkName: String?,
  sdkAdditionalData: PythonSdkAdditionalData? = null,
): PyResult<Sdk> {
  val pythonBinaryPathAsString = pythonBinaryPath.path.pathString
  val existingSdks = PythonSdkUtil.getAllSdks()
  existingSdks.find {
    it.sdkAdditionalData?.javaClass == sdkAdditionalData?.javaClass &&
    it.homePath == pythonBinaryPathAsString
  }?.let { return PyResult.success(it) }

  val pythonBinaryVirtualFile = withContext(Dispatchers.IO) {
    StandardFileSystems.local().refreshAndFindFileByPath(pythonBinaryPathAsString)
  } ?: return PyResult.localizedError(PyBundle.message("python.sdk.python.executable.not.found", pythonBinaryPath))

  val sdkName = suggestedSdkName ?: suggestAssociatedSdkName(pythonBinaryPathAsString, associatedModulePath)
  val sdk = SdkConfigurationUtil.setupSdk(
    existingSdks.toTypedArray(),
    pythonBinaryVirtualFile,
    PythonSdkType.getInstance(),
    false,
    sdkAdditionalData,
    sdkName)

  return sdk?.let { PyResult.success(it) }
         ?: PyResult.localizedError(PyBundle.message("python.sdk.failed.to.create.interpreter.title"))
}

@Internal
suspend fun <P : PathHolder> createSdk(
  pythonBinaryPath: P,
  suggestedSdkName: String,
  sdkAdditionalData: PythonSdkAdditionalData? = null,
): PyResult<Sdk> {
  val sdkType = PythonSdkType.getInstance()
  val existingSdks = PythonSdkUtil.getAllSdks()
  existingSdks.find {
    it.sdkAdditionalData?.javaClass == sdkAdditionalData?.javaClass &&
    it.homePath == pythonBinaryPath.toString()
  }?.let { return PyResult.success(it) }

  val sdk = when (pythonBinaryPath) {
    is PathHolder.Eel -> {
      val pythonBinaryVirtualFile = withContext(Dispatchers.IO) {
        VirtualFileManager.getInstance().refreshAndFindFileByNioPath(pythonBinaryPath.path)
      } ?: return PyResult.localizedError(PyBundle.message("python.sdk.python.executable.not.found", pythonBinaryPath))

      SdkConfigurationUtil.setupSdk(
        existingSdks.toTypedArray(),
        pythonBinaryVirtualFile,
        sdkType,
        false,
        sdkAdditionalData,
        suggestedSdkName
      )
    }
    is PathHolder.Target -> {
      SdkConfigurationUtil.createSdk(
        existingSdks,
        pythonBinaryPath.pathString,
        sdkType,
        sdkAdditionalData,
        suggestedSdkName
      ).also { sdk -> sdkType.setupSdkPaths(sdk) }
    }
  }

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
  val basePath = module?.baseDir?.path
  val associatedPath = associatedModulePath
  if (basePath != null && associatedPath == basePath) return true
  if (isAssociatedWithAnotherModule(module)) return false
  return isLocatedInsideModule(module) || containsModuleName(module)
}

@Internal
fun Sdk.isAssociatedWithAnotherModule(module: Module?): Boolean {
  val basePath = module?.baseDir?.path ?: return false
  val associatedPath = associatedModulePath ?: return false
  return basePath != associatedPath
}

@get:Internal
val Sdk.associatedModulePath: String?
  // TODO: Support .project associations
  get() = associatedPathFromAdditionalData /*?: associatedPathFromDotProject*/


@get:Internal
val Sdk.associatedModuleNioPath: Path?
  get() =
    try {
      associatedModulePath?.let { Path(it) }
    }
    catch (e: InvalidPathException) {
      if (getUserData(SDK_ERROR_REPORTED) != true) {
        LOGGER.warn("Can't convert ${associatedModulePath} to path", e)
        putUserData(SDK_ERROR_REPORTED, true)
      }
      null
    }

private val SDK_ERROR_REPORTED = Key.create<Boolean>("pySdkErrorReported")

@get:Internal
val Sdk.associatedModuleDir: VirtualFile?
  get() {
    val nioPath = associatedModuleNioPath ?: return null
    return VirtualFileManager.getInstance().findFileByNioPath(nioPath) ?: TempFileSystem.getInstance().findFileByNioFile(nioPath)
  }

@Internal
fun PyDetectedSdk.setup(existingSdks: List<Sdk>): Sdk? {
  val homeDir = homeDirectory ?: return null
  return SdkConfigurationUtil.setupSdk(existingSdks.toTypedArray(), homeDir, PythonSdkType.getInstance(), null, null)
}

@Internal
suspend fun PyDetectedSdk.setupSdk(
  module: Module,
  existingSdks: List<Sdk>,
  doAssociate: Boolean,
) {
  val newSdk = setupAssociated(existingSdks, module.baseDir?.path, doAssociate).getOr {
    ShowingMessageErrorSync.emit(it.error, module.project)
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
  flavorAndData: PyFlavorAndData<*, *> = PyFlavorAndData.UNKNOWN_FLAVOR_DATA,
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
    PyTargetAwareAdditionalData(flavorAndData).also {
      it.targetEnvironmentConfiguration = targetConfig
    }
  } ?: PythonSdkAdditionalData(flavorAndData)

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


/**
 * Please set sdk on module level: [Module.pythonSdk]
 */
@get:ApiStatus.Obsolete
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
  // please don't forget to update com.jetbrains.python.inspections.interpreter.PyInterpreterNotificationProvider (createCacheLoader)
  // after changing this method

  val baseSdkName = PythonSdkType.suggestBaseSdkName(sdkHome) ?: return@withContext null
  val venvRoot = PythonSdkUtil.getVirtualEnvRoot(sdkHome)?.path
  val associatedName = when {
    venvRoot != null && (associatedPath == null || !FileUtil.isAncestor(associatedPath, venvRoot, true)) ->
      PathUtil.getFileName(venvRoot)
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

@Internal
suspend fun PythonBinary.pyvenvContains(pattern: String): Boolean = withContext(Dispatchers.IO) {
  // TODO: Support for remote targets as well
  //  (probably the best way is to prepare a helper python script to check config file and run using exec service)
  val pyvenvFile = this@pyvenvContains.parent?.parent?.resolve("pyvenv.cfg")?.refreshAndFindVirtualFile() ?: return@withContext false
  val text = readAction { FileDocumentManager.getInstance().getDocument(pyvenvFile)?.text } ?: return@withContext false
  pattern in text
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
 *
 * @see PythonSdkFlavor.sdkSeemsValid
 */
val Sdk.sdkSeemsValid: Boolean
  get() {
    if (!isPythonSdk(this, true)) return false
    if (this.sdkAdditionalData == PyInvalidSdk) {
      return false
    }

    val pythonSdkAdditionalData = getOrCreateAdditionalData()
    return pythonSdkAdditionalData.flavorAndData.sdkSeemsValid(this, targetEnvConfiguration)
  }


@Internal
@Deprecated("Use module.pythonSdk", replaceWith = ReplaceWith("module.pythonSdk"), level = DeprecationLevel.ERROR)
fun setPythonSdk(module: Module, sdk: Sdk) {
  module.pythonSdk = sdk
}

@Internal
@Deprecated("Use module.pythonSdk", replaceWith = ReplaceWith("module.pythonSdk"), level = DeprecationLevel.ERROR)
fun getPythonSdk(module: Module): Sdk? = module.pythonSdk