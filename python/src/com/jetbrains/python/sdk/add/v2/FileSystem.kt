// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.execution.target.BrowsableTargetEnvironmentType
import com.intellij.execution.target.TargetBrowserHints
import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.execution.target.TargetEnvironmentRequest
import com.intellij.execution.target.getTargetType
import com.intellij.execution.target.joinTargetPaths
import com.intellij.execution.target.local.LocalTargetEnvironmentRequest
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextComponentAccessor
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.util.io.FileUtil
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.EelExecApi
import com.intellij.platform.eel.environmentVariables
import com.intellij.platform.eel.isWindows
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.provider.getEelDescriptor
import com.intellij.platform.eel.provider.localEel
import com.intellij.platform.eel.provider.toEelApi
import com.intellij.platform.eel.where
import com.intellij.python.community.execService.Args
import com.intellij.python.community.execService.BinOnEel
import com.intellij.python.community.execService.BinOnTarget
import com.intellij.python.community.execService.BinaryToExec
import com.intellij.python.community.execService.ExecService
import com.intellij.python.community.execService.execGetStdout
import com.intellij.python.community.execService.python.validatePythonAndGetInfo
import com.intellij.python.community.services.internal.impl.VanillaPythonWithPythonInfoImpl
import com.intellij.python.community.services.shared.VanillaPythonWithPythonInfo
import com.intellij.python.community.services.systemPython.SysPythonRegisterError
import com.intellij.python.community.services.systemPython.SystemPython
import com.intellij.python.community.services.systemPython.SystemPythonService
import com.intellij.python.venv.sdk.flavors.VirtualEnvSdkFlavor
import com.intellij.util.SlowOperations
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.PyToolUIInfo
import com.jetbrains.python.PythonInfo
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.MessageError
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.isCondaVirtualEnv
import com.jetbrains.python.isSuccess
import com.jetbrains.python.orLogException
import com.jetbrains.python.pathValidation.PlatformAndRoot
import com.jetbrains.python.pathValidation.PlatformAndRoot.Companion.getPlatformAndRoot
import com.jetbrains.python.pathValidation.ValidationRequest
import com.jetbrains.python.pathValidation.validateEmptyDir
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.run.PythonInterpreterTargetEnvironmentFactory
import com.jetbrains.python.sdk.BASE_DIR
import com.jetbrains.python.sdk.PyRemoteSdkAdditionalDataMarker
import com.jetbrains.python.sdk.PySdkSettings
import com.jetbrains.python.sdk.PythonSdkAdditionalData
import com.jetbrains.python.sdk.PythonSdkType
import com.jetbrains.python.sdk.PythonSdkUtil
import com.jetbrains.python.sdk.asBinToExecute
import com.jetbrains.python.sdk.associatedModulePath
import com.jetbrains.python.sdk.createSdk
import com.jetbrains.python.sdk.getSdksToInstall
import com.jetbrains.python.sdk.impl.PySdkBundle
import com.jetbrains.python.sdk.impl.resolvePythonBinary
import com.jetbrains.python.sdk.impl.resolvePythonHome
import com.jetbrains.python.sdk.isSystemWide
import com.jetbrains.python.target.PyTargetAwareAdditionalData
import com.jetbrains.python.target.PythonLanguageRuntimeConfiguration
import com.jetbrains.python.target.ui.TargetPanelExtension
import com.jetbrains.python.venvReader.VirtualEnvReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import java.nio.file.InvalidPathException
import java.nio.file.Path
import javax.swing.JComponent
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isExecutable
import kotlin.io.path.listDirectoryEntries


private val LOG: Logger = fileLogger()

internal class VenvAlreadyExistsError<P : PathHolder>(
  val detectedSelectableInterpreter: DetectedSelectableInterpreter<P>,
) : MessageError(message("python.add.sdk.already.contains.python.with.version", detectedSelectableInterpreter.pythonInfo.languageLevel))

data class EelFileSystem(
  val eelApi: EelApi,
) : FileSystem<PathHolder.Eel> {
  override val isBrowsable: Boolean = true
  override val isReadOnly: Boolean = false
  override val isLocal: Boolean = eelApi == localEel
  override val userReadableName: @NonNls String = eelApi.descriptor.name
  override val platformAndRoot: PlatformAndRoot = eelApi.getPlatformAndRoot()

  override fun getBinaryToExec(path: PathHolder.Eel, workingDir: Path?): BinaryToExec {
    return BinOnEel(path.path, workingDir)
  }

  override fun createTargetRequest(): TargetEnvironmentRequest = LocalTargetEnvironmentRequest()

  @RequiresEdt
  override fun <T> configureFileBrowseEditor(
    fieldAccessor: TextComponentAccessor<ComboBox<T>>,
    comboBox: ComboBox<T>,
    browseTitle: @Nls String,
    parentComponent: JComponent,
  ) {
    SlowOperations.knownIssue("PY-666").use { // TODO FIX ME PLEASE if you know how
      val descriptor = PythonSdkType.getInstance().homeChooserDescriptor.withTitle(browseTitle)
      FileChooser.chooseFile(descriptor, null, parentComponent, null) { file ->
        val path = file?.toNioPath()
        path?.toString()?.let {
          fieldAccessor.setText(comboBox, it)
        }
      }
    }
  }

  override suspend fun setupSdk(
    project: Project?,
    pythonBinaryPath: PathHolder.Eel,
    sdkAdditionalData: PythonSdkAdditionalData,
    targetPanelExtension: TargetPanelExtension?,
    suggestedSdkName: String?,
  ): PyResult<Sdk> {
    return createSdk(pythonBinaryPath, sdkAdditionalData, suggestedSdkName)
  }

  override fun parsePath(raw: String): PyResult<PathHolder.Eel> = try {
    Path.of(raw).let { path ->
      PyResult.success(PathHolder.Eel(path))
    }
  }
  catch (e: InvalidPathException) {
    PyResult.localizedError(e.localizedMessage)
  }

  override suspend fun validateExecutable(path: PathHolder.Eel): PyResult<Unit> {
    return when {
      !path.path.exists() -> PyResult.localizedError(message("sdk.create.not.executable.does.not.exist.error"))
      path.path.isDirectory() -> PyResult.localizedError(message("sdk.create.executable.directory.error"))
      !path.path.isExecutable() -> PyResult.localizedError(message("sdk.create.binary.not.executable"))
      else -> PyResult.success(Unit)
    }
  }

  override suspend fun fileExists(path: PathHolder.Eel): Boolean = path.path.exists()

  override suspend fun validateVenv(homePath: PathHolder.Eel): PyResult<Unit> = withContext(Dispatchers.IO) {
    val validationResult = when {
      !homePath.path.isAbsolute -> PyResult.localizedError(message("python.sdk.new.error.no.absolute"))
      homePath.path.exists() -> {
        val pythonBinaryPath = homePath.path.resolvePythonBinary()?.let { PathHolder.Eel(it) }
        val existingPython = pythonBinaryPath?.let { getSystemPythonFromSelection(it, requireSystemPython = false) }?.successOrNull
        if (existingPython == null) {
          PyResult.localizedError(message("sdk.create.custom.venv.folder.not.empty"))
        }
        else {
          PyResult.failure(VenvAlreadyExistsError(existingPython))
        }
      }
      else -> PyResult.success(Unit)
    }

    validationResult
  }

  override suspend fun suggestVenv(projectPath: Path): PyResult<PathHolder.Eel> = withContext(Dispatchers.IO) {
    val preferedFilePath = PySdkSettings.instance.getPreferredVirtualEnvBasePath(projectPath.toString())
    val suggestedVirtualEnvPath = FileUtil.toSystemDependentName(preferedFilePath)
    parsePath(suggestedVirtualEnvPath)
  }

  override suspend fun getSystemPythonFromSelection(
    pathToPython: PathHolder.Eel,
    requireSystemPython: Boolean,
  ): PyResult<DetectedSelectableInterpreter<PathHolder.Eel>> {
    val sysPythonValidationInfo = SystemPythonService().registerSystemPython(pathToPython.path)
    val (vanillaPython, isSystem) = when (sysPythonValidationInfo) {
      is Result.Failure -> {
        if (requireSystemPython) {
          // Not a system python, error
          return Result.failure(sysPythonValidationInfo.error.asPyError)
        }
        else {
          when (val r = sysPythonValidationInfo.error) {
            // Not a system python, but we are ok with it
            is SysPythonRegisterError.NotASystemPython -> Pair(r.notSystemPython, false)
            // Not a python at all
            is SysPythonRegisterError.PythonIsBroken -> {
              return Result.failure(r.asPyError)
            }
          }
        }
      }
      // Perfectly valid system python
      is Result.Success -> Pair(sysPythonValidationInfo.result, true)
    }
    val interpreter = DetectedSelectableInterpreter(
      homePath = PathHolder.Eel(vanillaPython.pythonBinary),
      pythonInfo = vanillaPython.pythonInfo,
      isBase = isSystem
    )

    return PyResult.success(interpreter)
  }

  override suspend fun wrapSdk(sdk: Sdk): SdkWrapper<PathHolder.Eel> = withContext(Dispatchers.IO) {
    val adjustedHomePath = PythonSdkType.getInstance().adjustSelectedSdkHome(sdk.homePath!!)
    SdkWrapper(sdk, PathHolder.Eel(Path.of(adjustedHomePath)))
  }

  override suspend fun detectSelectableVenv(projectPathPrefix: Path): List<DetectedSelectableInterpreter<PathHolder.Eel>> {
    // Venvs are not detected manually, but must migrate to VenvService or so
    val context: UserDataHolder = UserDataHolderBase()
    context.putUserData(BASE_DIR, projectPathPrefix)
    val pythonBinaries = VirtualEnvSdkFlavor.getInstance().suggestLocalHomePaths(null, context)
    val suggestedPythonBinaries = VanillaPythonWithPythonInfoImpl.createByPythonBinaries(pythonBinaries)

    val venvs: List<VanillaPythonWithPythonInfo> = suggestedPythonBinaries.mapNotNull { (venv, r) ->
      when (r) {
        is Result.Failure -> {
          fileLogger().warn("Skipping $venv : ${r.error}")
          null
        }
        is Result.Success -> r.result
      }
    }

    // System (base) pythons
    val system: List<SystemPython> = SystemPythonService().findSystemPythons(eelApi)

    // Python + isBase. Both: system and venv.
    val detected = run {
      venvs.map { Triple(it, false, null) } +
      system.map { Triple(it, true, it.ui) }
    }.map { (python, base, ui) ->
      DetectedSelectableInterpreter(
        homePath = PathHolder.Eel(python.pythonBinary),
        pythonInfo = python.pythonInfo,
        isBase = base,
        ui = ui
      )
    }.sorted()

    return detected
  }

  override fun preferredInterpreterBasePath(): PathHolder.Eel? = when (eelApi) {
    localEel -> {
      PySdkSettings.instance.preferredVirtualEnvBaseSdk?.let {
        PathHolder.Eel(Path.of(it))
      }
    }
    else -> null
  }

  override fun resolvePythonBinary(pythonHome: PathHolder.Eel): PathHolder.Eel? {
    return pythonHome.path.resolvePythonBinary()?.let { PathHolder.Eel(it) }
  }

  override fun resolvePythonHome(pythonBinary: PathHolder.Eel): PathHolder.Eel {
    return PathHolder.Eel(pythonBinary.path.resolvePythonHome())
  }

  override fun getVenvName(pythonHome: PathHolder.Eel): String? {
    return resolvePythonBinary(pythonHome)?.let { VirtualEnvReader().getVenvName(it.path) }
  }

  override suspend fun getHomePath(): PathHolder.Eel = PathHolder.Eel(eelApi.userInfo.home.asNioPath())

  override fun normalizePathToRemote(path: PathHolder.Eel): PathHolder.Eel = path

  override suspend fun detectEnvironments(
    workingDir: Path,
    uiInfoGetter: (PathHolder.Eel) -> PyToolUIInfo?,
  ): List<DetectedSelectableInterpreter<PathHolder.Eel>> {
    if (workingDir.getEelDescriptor().toEelApi() != eelApi) return emptyList()

    return withContext(Dispatchers.IO) {
      workingDir.listDirectoryEntries().filter { it.isDirectory() }.mapNotNull { possibleVenvHome ->
        val pythonBinary = resolvePythonBinary(PathHolder.Eel(possibleVenvHome)) ?: return@mapNotNull null
        val pythonInfo = pythonBinary.path.validatePythonAndGetInfo().successOrNull ?: return@mapNotNull null
        val ui = uiInfoGetter(pythonBinary)
        DetectedSelectableInterpreter(pythonBinary, pythonInfo, false, ui)
      }
    }
  }

  override suspend fun detectTool(
    toolName: String,
    additionalSearchPaths: List<PathHolder.Eel>,
    filter: (PathHolder.Eel) -> Boolean,
  ): PathHolder.Eel? = withContext(Dispatchers.IO) {
    val fromPath = eelApi.exec.where(toolName)
      ?.asNioPath()
      ?.let { PathHolder.Eel(it) }
      ?.takeIf(filter)
    if (fromPath != null) return@withContext fromPath

    val binaryNames =
      if (eelApi.platform.isWindows) listOf("$toolName.exe", "$toolName.bat")
      else listOf(toolName)
    for (path in additionalSearchPaths) {
      assert(path.path.getEelDescriptor() == eelApi.descriptor) {
        "Additional search paths should be on the same descriptor as EelFileSystem API, but $path isn't on $eelApi"
      }
      for (binaryName in binaryNames) {
        val candidate = path.path.resolve(binaryName)
          .takeIf { it.isExecutable() }
          ?.let { PathHolder.Eel(it) }
          ?.takeIf(filter)
        if (candidate != null) return@withContext candidate
      }
    }

    null
  }

  override suspend fun getFullPath(prefixEnvVar: String, pathComponents: List<String>): PathHolder.Eel? {
    val prefix = try {
      eelApi.exec.environmentVariables().eelIt().await()[prefixEnvVar] ?: return null
    }
    catch (e: EelExecApi.EnvironmentVariablesException) {
      LOG.warn("Cannot get environment variables from eel", e)
      return null
    }

    val prefixResolvedPath = parsePath(prefix).successOrNull ?: return null
    return PathHolder.Eel(pathComponents.fold(prefixResolvedPath.path, Path::resolve))
  }

  override suspend fun getFullPathFromHome(pathComponents: List<String>): PathHolder.Eel {
    val home = eelApi.userInfo.home.asNioPath()
    return PathHolder.Eel(pathComponents.fold(home, Path::resolve))
  }

  override suspend fun resolveInWorkingDir(workingDir: Path, dirName: String): PathHolder.Eel {
    return PathHolder.Eel(workingDir.resolve(dirName))
  }
}

data class TargetFileSystem(
  val targetEnvironmentConfiguration: TargetEnvironmentConfiguration,
  val pythonLanguageRuntimeConfiguration: PythonLanguageRuntimeConfiguration,
) : FileSystem<PathHolder.Target> {
  override val isReadOnly: Boolean
    get() = !PythonInterpreterTargetEnvironmentFactory.isMutable(targetEnvironmentConfiguration)
  override val isBrowsable: Boolean
    get() = targetEnvironmentConfiguration.getTargetType() is BrowsableTargetEnvironmentType
  override val isLocal: Boolean = false
  override val userReadableName: @NonNls String = targetEnvironmentConfiguration.displayName
  override val platformAndRoot: PlatformAndRoot = targetEnvironmentConfiguration.getPlatformAndRoot()

  private val systemPythonCache = ArrayList<DetectedSelectableInterpreter<PathHolder.Target>>()
  private lateinit var shellImpl: String
  private lateinit var home: PathHolder.Target

  override fun parsePath(raw: String): PyResult<PathHolder.Target> {
    return PyResult.success(PathHolder.Target(raw))
  }

  override fun createTargetRequest(): TargetEnvironmentRequest =
    targetEnvironmentConfiguration.createEnvironmentRequest(project = null)

  @RequiresEdt
  override fun <T> configureFileBrowseEditor(
    fieldAccessor: TextComponentAccessor<ComboBox<T>>,
    comboBox: ComboBox<T>,
    browseTitle: @Nls String,
    parentComponent: JComponent,
  ) {
    val targetType = targetEnvironmentConfiguration.getTargetType()
    if (targetType is BrowsableTargetEnvironmentType) {
      val descriptor = FileChooserDescriptorFactory.singleFile().withTitle(browseTitle)
      val hints = TargetBrowserHints(showLocalFsInBrowser = true, descriptor)

      val actionListener = targetType.createBrowser(
        ProjectManager.getInstance().defaultProject,
        hints.customFileChooserDescriptor!!.title,
        fieldAccessor,
        comboBox,
        { targetEnvironmentConfiguration },
        hints
      )
      actionListener.actionPerformed(null)
    }
    else {
      val dialog = ManualPathEntryDialog(browseTitle, parentComponent.width, targetEnvironmentConfiguration)
      if (dialog.showAndGet()) {
        fieldAccessor.setText(comboBox, dialog.path)
      }
    }
  }

  override suspend fun setupSdk(
    project: Project?,
    pythonBinaryPath: PathHolder.Target,
    sdkAdditionalData: PythonSdkAdditionalData,
    targetPanelExtension: TargetPanelExtension?,
    suggestedSdkName: String?,
  ): PyResult<Sdk> {
    val languageLevel = getBinaryToExec(pythonBinaryPath).validatePythonAndGetInfo().getOr { return it }.languageLevel

    val (additionalData, customSdkSuggestedName) = run {
      val flavorAndData = sdkAdditionalData.flavorAndData
      val data = PyTargetAwareAdditionalData(flavorAndData).also {
        it.interpreterPath = pythonBinaryPath.toString()
        it.targetEnvironmentConfiguration = targetEnvironmentConfiguration
      }
      targetPanelExtension?.let {
        it.applyToTargetConfiguration()
        it.applyToAdditionalData(data)
      }
      val name = PythonInterpreterTargetEnvironmentFactory.findDefaultSdkName(project, data, languageLevel.toPythonVersion())
      data to name
    }

    return createSdk(
      pythonBinaryPath,
      additionalData,
      suggestedSdkName ?: customSdkSuggestedName
    )
  }

  /**
   * Currently, we don't validate the executable on target because there is no API to check its type on target.
   */
  override suspend fun validateExecutable(path: PathHolder.Target): PyResult<Unit> =
    if (fileExists(path)) {
      PyResult.success(Unit)
    }
    else PyResult.localizedError(message("sdk.create.not.executable.does.not.exist.error"))

  override suspend fun fileExists(path: PathHolder.Target): Boolean {
    return executeCommand("test -f ${path.pathString}").isSuccess
  }

  override suspend fun validateVenv(homePath: PathHolder.Target): PyResult<Unit> = withContext(Dispatchers.IO) {
    val pythonBinaryPath = resolvePythonBinary(homePath)

    val existingPython = getSystemPythonFromSelection(pythonBinaryPath, requireSystemPython = false).successOrNull
    val validationResult = if (existingPython == null) {
      val validationInfo = validateEmptyDir(
        ValidationRequest(
          path = homePath.pathString,
          fieldIsEmpty = PySdkBundle.message("python.venv.location.field.empty"),
          platformAndRoot = targetEnvironmentConfiguration.getPlatformAndRoot()
        ),
        notADirectory = PySdkBundle.message("python.venv.location.field.not.directory"),
        directoryNotEmpty = PySdkBundle.message("python.venv.location.directory.not.empty")
      )
      if (validationInfo != null) {
        PyResult.failure(ValidationInfoError(validationInfo))
      }
      else {
        PyResult.success(Unit)
      }
    }
    else {
      PyResult.failure(VenvAlreadyExistsError(existingPython))
    }

    validationResult
  }

  override suspend fun suggestVenv(projectPath: Path): PyResult<PathHolder.Target> = withContext(Dispatchers.IO) {
    val homePathString = when {
      projectPath.toString().isEmpty() -> pythonLanguageRuntimeConfiguration.userHome
      else -> joinTargetPaths(pythonLanguageRuntimeConfiguration.userHome, VirtualEnvReader.DEFAULT_VIRTUALENVS_DIR,
                              projectPath.fileName.toString(), fileSeparator = '/')
    }

    PyResult.success(PathHolder.Target(homePathString))
  }

  private suspend fun registerSystemPython(pathToPython: PathHolder.Target): PyResult<DetectedSelectableInterpreter<PathHolder.Target>> {
    val pythonBinaryToExec = getBinaryToExec(pathToPython)
    val pythonInfo = pythonBinaryToExec.validatePythonAndGetInfo().getOr {
      return it
    }

    val interpreter = DetectedSelectableInterpreter(
      homePath = pathToPython,
      pythonInfo = pythonInfo,
      true,
    ).also {
      systemPythonCache.add(it)
    }

    return PyResult.success(interpreter)
  }

  override suspend fun wrapSdk(sdk: Sdk): SdkWrapper<PathHolder.Target> {
    return SdkWrapper(sdk, PathHolder.Target(sdk.homePath!!))
  }

  override fun getBinaryToExec(path: PathHolder.Target, workingDir: Path?): BinaryToExec {
    return BinOnTarget(path.pathString, targetEnvironmentConfiguration, workingDir)
  }

  override suspend fun getSystemPythonFromSelection(
    pathToPython: PathHolder.Target,
    requireSystemPython: Boolean,
  ): PyResult<DetectedSelectableInterpreter<PathHolder.Target>> {
    return registerSystemPython(pathToPython)
  }

  override suspend fun detectSelectableVenv(projectPathPrefix: Path): List<DetectedSelectableInterpreter<PathHolder.Target>> {
    val fullPathOnTarget = pythonLanguageRuntimeConfiguration.pythonInterpreterPath
    val pathHolder = PathHolder.Target(fullPathOnTarget)
    val systemPython = getSystemPythonFromSelection(pathHolder, requireSystemPython = false).getOr { return emptyList() }
    return listOf(systemPython)
  }

  override fun resolvePythonBinary(pythonHome: PathHolder.Target): PathHolder.Target {
    val pythonHomeString = pythonHome.pathString
    val platform = targetEnvironmentConfiguration.getPlatformAndRoot().platform
    return PathHolder.Target(VirtualEnvReader().findPythonInPythonRootForTarget(pythonHomeString, platform))
  }

  override fun resolvePythonHome(pythonBinary: PathHolder.Target): PathHolder.Target {
    return PathHolder.Target(pythonBinary.pathString.substringBeforeLast("/bin/"))
  }

  override fun getVenvName(pythonHome: PathHolder.Target): String? {
    val pythonBinary = resolvePythonBinary(pythonHome)
    val pythonBinaryString = pythonBinary.pathString
    val platform = targetEnvironmentConfiguration.getPlatformAndRoot().platform
    return VirtualEnvReader().getVenvNameForTarget(pythonBinaryString, platform)
  }

  override suspend fun getHomePath(): PathHolder.Target? {
    if (!this::home.isInitialized) {
      val homeValue = getEnvVar("HOME").successOrNull ?: return null
      home = PathHolder.Target(homeValue)
    }
    return home
  }

  override fun normalizePathToRemote(path: PathHolder.Target): PathHolder.Target {
    val mapper = PythonInterpreterTargetEnvironmentFactory.getTargetWithMappedLocalVfs(targetEnvironmentConfiguration)
    val targetPath = mapper?.getTargetPath(Path.of(path.pathString)) ?: path.pathString
    return PathHolder.Target(targetPath)
  }

  // TODO PY-87712 Support detection for remotes
  override suspend fun detectEnvironments(
    workingDir: Path,
    uiInfoGetter: (PathHolder.Target) -> PyToolUIInfo?,
  ): List<DetectedSelectableInterpreter<PathHolder.Target>> {
    return emptyList()
  }

  private suspend fun which(cmd: String): PathHolder.Target? {
    val binaryPathString = executeCommand("which $cmd").getOr { return null }
    val binaryPathOnFS = parsePath(binaryPathString).getOr { return null }
    return binaryPathOnFS
  }

  override suspend fun detectTool(
    toolName: String,
    additionalSearchPaths: List<PathHolder.Target>,
    filter: (PathHolder.Target) -> Boolean,
  ): PathHolder.Target? = withContext(Dispatchers.IO) {
    val fromWhich = which(toolName)?.takeIf(filter)
    if (fromWhich != null) return@withContext fromWhich

    for (path in additionalSearchPaths) {
      val candidate = parsePath("${path.pathString}/$toolName").successOrNull
        ?.takeIf { filter(it) && fileExists(it) }
      if (candidate != null) return@withContext candidate
    }

    null
  }

  override suspend fun getFullPath(prefixEnvVar: String, pathComponents: List<String>): PathHolder.Target? {
    val prefix = getEnvVar(prefixEnvVar).successOrNull ?: return null
    return getFullPathWithPrefix(prefix, pathComponents)
  }

  override suspend fun getFullPathFromHome(pathComponents: List<String>): PathHolder.Target? {
    val homePath = getHomePath()?.pathString ?: return null
    return getFullPathWithPrefix(homePath, pathComponents)
  }

  override suspend fun resolveInWorkingDir(workingDir: Path, dirName: String): PathHolder.Target? {
    val remoteWorkingDir = executeCommand("pwd", workingDir).successOrNull ?: return null
    return PathHolder.Target("$remoteWorkingDir/$dirName")
  }

  private fun getFullPathWithPrefix(prefix: String, pathComponents: List<String>): PathHolder.Target {
    val resolvedPath = if (pathComponents.isEmpty()) prefix else "$prefix/${pathComponents.joinToString("/")}"
    return PathHolder.Target(resolvedPath)
  }

  private suspend fun executeCommand(cmd: String, workingDir: Path? = null): PyResult<String> {
    val shell = getShell()
    val bin = getBinaryToExec(PathHolder.Target(shell), workingDir)
    return ExecService().execGetStdout(bin, Args("-l", "-c", cmd))
  }

  private suspend fun getEnvVar(envVarName: String): PyResult<String> = executeCommand("printenv ${envVarName}")

  private suspend fun getShell(): String {
    if (!this::shellImpl.isInitialized) {
      shellImpl = getShellImpl().orLogException(LOG) ?: "/bin/sh"
    }
    return shellImpl
  }

  private suspend fun getShellImpl(): PyResult<String> {
    val bin1 = getBinaryToExec(PathHolder.Target("getent"))
    val execService = ExecService()
    val res = execService.execGetStdout(bin1, Args("passwd")).getOr { return it }
    val bin2 = getBinaryToExec(PathHolder.Target("whoami"))
    val user = execService.execGetStdout(bin2).getOr { return it }
    val shell = res.lines().firstOrNull { it.substringBefore(':').contains(user) }?.substringAfterLast(':')
    @Suppress("HardCodedStringLiteral")
    return shell?.let { PyResult.success(it) } ?: PyResult.localizedError("Could not get shell")
  }
}

/**
 * Returns a [EelFileSystem] backed by the EEL of [this] path, falling back to [localEel] when [this] is null
 * or has no EEL descriptor. Convenience for callers that operate on local-host paths and want a [FileSystem]
 * to pass to a tool runner.
 */
internal suspend fun Path?.toEelFileSystem(): EelFileSystem =
  EelFileSystem(this?.getEelDescriptor()?.toEelApi() ?: localEel)

internal fun <P : PathHolder> FileSystem<P>.getInstallableInterpreters(): List<InstallableSelectableInterpreter<P>> =
  if (isLocal) {
    getSdksToInstall()
      .mapNotNull { sdk ->
        LanguageLevel.fromPythonVersionSafe(sdk.installation.release.version)?.let { it to sdk }
      }
      .sortedByDescending { it.first }
      .map { (languageLevel, sdk) ->
        InstallableSelectableInterpreter(PythonInfo(languageLevel), sdk)
      }
  }
  else emptyList()

internal suspend fun <P : PathHolder> FileSystem<P>.getExistingSelectableInterpreters(
  projectPathPrefix: Path,
): List<ExistingSelectableInterpreter<P>> = withContext(Dispatchers.IO) {
  if (!isLocal) return@withContext emptyList()

  val allValidSdks = PythonSdkUtil
    .getAllSdks()
    .filter { sdk ->
      if (sdk.isCondaVirtualEnv) return@filter false
      if (sdk.sdkAdditionalData is PyRemoteSdkAdditionalDataMarker) return@filter false

      try {
        val associatedModulePath = sdk.associatedModulePath?.let { Path(it) } ?: return@filter true
        associatedModulePath.startsWith(projectPathPrefix)
      }
      catch (e: InvalidPathException) {
        LOG.warn("Skipping bad association ${sdk.associatedModulePath}", e)
        false
      }
    }.mapNotNull { sdk ->
      val languageLevel = sdk.versionString?.let {
        LanguageLevel.getLanguageLevelFromVersionStringStaticSafe(it)
      } ?: run {
        val binToExecute = sdk.asBinToExecute().orLogException(LOG)
        val pythonInfo = binToExecute?.let {
          ExecService().validatePythonAndGetInfo(binToExecute).orLogException(LOG)
        }
        pythonInfo?.languageLevel
      }

      languageLevel?.let {
        ExistingSelectableInterpreter<P>(wrapSdk(sdk), PythonInfo(it), sdk.isSystemWide)
      }
    }
  allValidSdks
}
