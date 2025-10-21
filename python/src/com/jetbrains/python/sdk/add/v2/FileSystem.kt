// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.execution.target.BrowsableTargetEnvironmentType
import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.execution.target.getTargetType
import com.intellij.execution.target.joinTargetPaths
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.io.FileUtil
import com.intellij.platform.eel.EelApi
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.eel.provider.localEel
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
import com.intellij.python.community.services.systemPython.SystemPython
import com.intellij.python.community.services.systemPython.SystemPythonService
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.PythonInfo
import com.jetbrains.python.Result
import com.jetbrains.python.errorProcessing.MessageError
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.getOrLogException
import com.jetbrains.python.pathValidation.PlatformAndRoot.Companion.getPlatformAndRoot
import com.jetbrains.python.pathValidation.ValidationRequest
import com.jetbrains.python.pathValidation.validateEmptyDir
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.run.PythonInterpreterTargetEnvironmentFactory
import com.jetbrains.python.sdk.*
import com.jetbrains.python.sdk.flavors.PythonSdkFlavor
import com.jetbrains.python.sdk.flavors.VirtualEnvSdkFlavor
import com.jetbrains.python.sdk.impl.PySdkBundle
import com.jetbrains.python.sdk.impl.resolvePythonBinary
import com.jetbrains.python.target.PythonLanguageRuntimeConfiguration
import com.jetbrains.python.venvReader.VirtualEnvReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.InvalidPathException
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.exists


private val LOG: Logger = fileLogger()


data class SdkWrapper<P>(val sdk: Sdk, val homePath: P)

internal class VenvAlreadyExistsError<P : PathHolder>(
  val detectedSelectableInterpreter: DetectedSelectableInterpreter<P>,
) : MessageError(message("python.add.sdk.already.contains.python.with.version", detectedSelectableInterpreter.pythonInfo.languageLevel))

sealed interface FileSystem<P : PathHolder> {
  val isReadOnly: Boolean
  val isBrowseable: Boolean

  fun parsePath(raw: String): PyResult<P>

  suspend fun getSystemPythonFromSelection(pathToPython: P): PyResult<DetectedSelectableInterpreter<P>>

  suspend fun validateVenv(homePath: P): PyResult<Unit>
  suspend fun suggestVenv(projectPath: Path): PyResult<P>
  fun wrapSdk(sdk: Sdk): SdkWrapper<P>
  suspend fun detectSelectableVenv(): List<DetectedSelectableInterpreter<P>>
  fun preferredInterpreterBasePath(): P? = null
  suspend fun resolvePythonBinary(pythonHome: P): P?

  fun getBinaryToExec(path: P): BinaryToExec
  suspend fun which(cmd: String): P?

  data class Eel(
    val eelApi: EelApi,
  ) : FileSystem<PathHolder.Eel> {
    override val isBrowseable: Boolean = true
    override val isReadOnly: Boolean = false
    override fun getBinaryToExec(path: PathHolder.Eel): BinaryToExec {
      return BinOnEel(path.path)
    }

    override fun parsePath(raw: String): PyResult<PathHolder.Eel> = try {
      Path.of(raw).let { path ->
        PyResult.success(PathHolder.Eel(path))
      }
    }
    catch (e: InvalidPathException) {
      PyResult.localizedError(e.localizedMessage)
    }

    override suspend fun validateVenv(homePath: PathHolder.Eel): PyResult<Unit> = withContext(Dispatchers.IO) {
      val validationResult = when {
        !homePath.path.isAbsolute -> PyResult.localizedError(message("python.sdk.new.error.no.absolute"))
        homePath.path.exists() -> {
          val pythonBinaryPath = homePath.path.resolvePythonBinary()?.let { PathHolder.Eel(it) }
          val existingPython = pythonBinaryPath?.let { getSystemPythonFromSelection(it) }?.successOrNull
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

    override suspend fun getSystemPythonFromSelection(pathToPython: PathHolder.Eel): PyResult<DetectedSelectableInterpreter<PathHolder.Eel>> {
      val systemPython = SystemPythonService().registerSystemPython(pathToPython.path).getOr { return it }
      val interpreter = DetectedSelectableInterpreter(
        homePath = PathHolder.Eel(systemPython.pythonBinary),
        pythonInfo = systemPython.pythonInfo,
        isBase = true
      )

      return PyResult.success(interpreter)
    }

    override fun wrapSdk(sdk: Sdk): SdkWrapper<PathHolder.Eel> {
      return SdkWrapper(sdk, PathHolder.Eel(Path.of(sdk.homePath!!)))
    }

    override suspend fun detectSelectableVenv(): List<DetectedSelectableInterpreter<PathHolder.Eel>> {
      // Venvs are not detected manually, but must migrate to VenvService or so
      val pythonBinaries = VirtualEnvSdkFlavor.getInstance().suggestLocalHomePaths(null, null)
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

    override suspend fun resolvePythonBinary(pythonHome: PathHolder.Eel): PathHolder.Eel? {
      return pythonHome.path.resolvePythonBinary()?.let { PathHolder.Eel(it) }
    }

    override suspend fun which(cmd: String): PathHolder.Eel? {
      return eelApi.exec.where(cmd)?.asNioPath()?.let { PathHolder.Eel(it) }
    }
  }

  data class Target(
    val targetEnvironmentConfiguration: TargetEnvironmentConfiguration,
    val pythonLanguageRuntimeConfiguration: PythonLanguageRuntimeConfiguration,
  ) : FileSystem<PathHolder.Target> {
    override val isReadOnly: Boolean
      get() = !PythonInterpreterTargetEnvironmentFactory.isMutable(targetEnvironmentConfiguration)
    override val isBrowseable: Boolean
      get() = targetEnvironmentConfiguration.getTargetType() is BrowsableTargetEnvironmentType

    private val systemPythonCache = ArrayList<DetectedSelectableInterpreter<PathHolder.Target>>()

    override fun parsePath(raw: String): PyResult<PathHolder.Target> {
      return PyResult.success(PathHolder.Target(raw))
    }

    override suspend fun validateVenv(homePath: PathHolder.Target): PyResult<Unit> = withContext(Dispatchers.IO) {
      val pythonBinaryPath = resolvePythonBinary(homePath)

      val existingPython = getSystemPythonFromSelection(pythonBinaryPath).successOrNull
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

    override fun wrapSdk(sdk: Sdk): SdkWrapper<PathHolder.Target> {
      return SdkWrapper(sdk, PathHolder.Target(sdk.homePath!!))
    }

    override fun getBinaryToExec(path: PathHolder.Target): BinaryToExec {
      return BinOnTarget(path.pathString, targetEnvironmentConfiguration)
    }

    override suspend fun getSystemPythonFromSelection(pathToPython: PathHolder.Target): PyResult<DetectedSelectableInterpreter<PathHolder.Target>> {
      return registerSystemPython(pathToPython)
    }

    override suspend fun detectSelectableVenv(): List<DetectedSelectableInterpreter<PathHolder.Target>> {
      val fullPathOnTarget = pythonLanguageRuntimeConfiguration.pythonInterpreterPath
      val pathHolder = PathHolder.Target(fullPathOnTarget)
      val systemPython = getSystemPythonFromSelection(pathHolder).getOr { return emptyList() }
      return listOf(systemPython)
    }

    override suspend fun resolvePythonBinary(pythonHome: PathHolder.Target): PathHolder.Target {
      val pythonHomeString = pythonHome.pathString
      val pythonBinaryPath = when {
        pythonHomeString.contains("\\") -> "${pythonHomeString.removeSuffix("\\")}\\Scripts\\python.exe"
        else -> "${pythonHomeString.removeSuffix("/")}/bin/python"
      }.let { PathHolder.Target(it) }

      return pythonBinaryPath
    }

    override suspend fun which(cmd: String): PathHolder.Target? {
      val which = getBinaryToExec(PathHolder.Target("which"))
      val condaPathString = ExecService().execGetStdout(which, Args(cmd)).getOr { return null }
      val condaPathOnFS = parsePath(condaPathString).getOr { return null }
      return condaPathOnFS
    }
  }
}

internal fun <P : PathHolder> FileSystem<P>.getInstallableInterpreters(): List<InstallableSelectableInterpreter<P>> = when ((this as? FileSystem.Eel)?.eelApi) {
  localEel -> {
    getSdksToInstall()
      .mapNotNull { sdk ->
        LanguageLevel.fromPythonVersionSafe(sdk.installation.release.version)?.let { it to sdk }
      }
      .sortedByDescending { it.first }
      .map { (languageLevel, sdk) ->
        InstallableSelectableInterpreter(PythonInfo(languageLevel), sdk)
      }
  }
  else -> emptyList()
}

internal suspend fun <P : PathHolder> FileSystem<P>.getExistingSelectableInterpreters(
  projectPathPrefix: Path,
): List<ExistingSelectableInterpreter<P>> = withContext(Dispatchers.IO) {
  if ((this@getExistingSelectableInterpreters as? FileSystem.Eel)?.eelApi != localEel) return@withContext emptyList()

  val allValidSdks = PythonSdkUtil
    .getAllSdks()
    .filter { sdk ->
      if (sdk.targetAdditionalData != null) return@filter false

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
        PythonSdkFlavor.getLanguageLevelFromVersionStringStaticSafe(it)
      } ?: run {
        ExecService().validatePythonAndGetInfo(sdk.asBinToExecute()).getOrLogException(LOG)?.languageLevel
      }

      languageLevel?.let {
        ExistingSelectableInterpreter<P>(wrapSdk(sdk), PythonInfo(it), sdk.isSystemWide)
      }
    }
  allValidSdks
}

internal suspend fun <P : PathHolder> FileSystem<P>.getDetectedSelectableInterpreters(
  existingSelectableInterpreters: List<ExistingSelectableInterpreter<P>>,
): List<DetectedSelectableInterpreter<P>> = withContext(Dispatchers.IO) {
  val existingSdkPaths = existingSelectableInterpreters.map { it.homePath }.toSet()
  val detected = detectSelectableVenv().filterNot { it.homePath in existingSdkPaths }
  detected
}
