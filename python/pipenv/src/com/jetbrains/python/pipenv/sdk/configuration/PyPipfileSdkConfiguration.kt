// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.pipenv.sdk.configuration

import com.intellij.python.sdk.backend.resolvePythonHome
import com.intellij.python.community.execService.EnvVarVal
import kotlin.io.path.exists
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.python.community.execService.EnvVar
import com.intellij.python.sdk.common.PyInterpreterRef
import com.intellij.python.sdk.backend.resolveHomeEnvRef
import com.intellij.python.sdk.backend.PythonInterpreterKey
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.python.community.common.tools.ToolId
import com.intellij.python.community.execService.ZeroCodeStdoutParserTransformer
import com.intellij.python.community.impl.pipenv.PipEnvBundle
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.project.PyProject
import com.jetbrains.python.project.getEel
import com.jetbrains.python.project.project
import com.jetbrains.python.project.resolveFile
import com.jetbrains.python.sdk.add.v2.PathHolder
import com.jetbrains.python.sdk.configuration.CreateInterpreterInfo
import com.jetbrains.python.sdk.configuration.EnvCheckerResult
import com.jetbrains.python.sdk.configuration.PIPENV_TOOL_ID
import com.jetbrains.python.sdk.configuration.PyProjectSdkConfigurationExtension
import com.jetbrains.python.sdk.configuration.PyProjectTomlConfigurationExtension
import com.jetbrains.python.sdk.configuration.PySdkConfigurationCollector
import com.jetbrains.python.sdk.configuration.PySdkConfigurationCollector.PipEnvResult
import com.jetbrains.python.sdk.configuration.findEnvOrNull
import com.jetbrains.python.sdk.configuration.prepareSdkCreator
import com.jetbrains.python.sdk.createSdk
import com.intellij.python.sdk.backend.PySdkBundle
import com.intellij.python.sdk.backend.resolvePythonBinary
import com.jetbrains.python.sdk.pipenv.PIP_FILE
import com.jetbrains.python.sdk.pipenv.PyPipEnvSdkAdditionalData
import com.intellij.python.community.impl.pipenv.PipEnvPyTool
import com.intellij.python.sdk.backend.resolveExecutable
import com.intellij.python.sdk.backend.PythonInterpreter
import com.jetbrains.python.sdk.add.v2.toFileSystem
import com.jetbrains.python.sdk.add.v2.EelOrJustPath.Companion.asEelOrJustPath
import com.jetbrains.python.sdk.pipenv.runPipEnv
import com.jetbrains.python.sdk.pipenv.setupPipEnv
import com.jetbrains.python.sdk.pipenv.suggestedSdkName
import com.jetbrains.python.venvReader.VirtualEnvReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path
import kotlin.io.path.isExecutable
import kotlin.io.path.pathString

private val LOGGER = Logger.getInstance(PyPipfileSdkConfiguration::class.java)

internal class PyPipfileSdkConfiguration : PyProjectSdkConfigurationExtension {

  override val toolId: ToolId = PIPENV_TOOL_ID

  override val potentialDependencyFiles: Set<String> = setOf(PIP_FILE)

  override suspend fun checkEnvironmentAndPrepareSdkCreator(pyProject: PyProject, venvs: List<PythonBinary>): CreateInterpreterInfo? =
    prepareSdkCreator(
      { checkManageableEnv(pyProject) }
    ) { { createAndAddSdk(pyProject) } }

  override fun asPyProjectTomlSdkConfigurationExtension(): PyProjectTomlConfigurationExtension? = null

  /**
   * The pipenv environment that the entry names by its path: the `.venv` of the project, made with
   * `PIPENV_VENV_IN_PROJECT`, or an absolute home, made with `WORKON_HOME` and `PIPENV_CUSTOM_VENV_NAME`. So it stands
   * where the entry says, also after the project moved. Another path, and an environment that is there, give nothing.
   */
  override suspend fun prepareSdkCreatorFor(pyProject: PyProject, key: PythonInterpreterKey): CreateInterpreterInfo? {
    if (key.ref.manager != PipEnvPyTool.getInstance().fusId) return null
    when (key.ref.mode) {
      PyInterpreterRef.Mode.Native -> Unit
      is PyInterpreterRef.Mode.Target -> return null
    }
    val home = resolveHomeEnvRef(key.ownerDir, key.ref.env.envRef)
               ?: key.ref.env.envRef.value.toNioPathOrNull()?.resolvePythonHome()
               ?: return null
    if (withContext(Dispatchers.IO) { home.exists() }) return null
    val baseEnv = when {
      home == pyProject.baseDir.resolve(VirtualEnvReader.DEFAULT_VIRTUALENV_DIRNAME) -> listOf(EnvVar("PIPENV_VENV_IN_PROJECT", "1"))
      // A relative path other than the `.venv` of the project is no place pipenv puts an environment.
      home.startsWith(key.ownerDir) -> return null
      else -> {
        val parent = home.parent ?: return null
        listOf(EnvVar("WORKON_HOME", EnvVarVal.NioPath(parent)), EnvVar("PIPENV_CUSTOM_VENV_NAME", home.fileName.toString()))
      }
    }
    return prepareSdkCreator(
      { EnvCheckerResult.EnvNotFound(PipEnvBundle.message("sdk.recreate.pipenv.environment")) }
    ) { { createAndAddSdk(pyProject, baseEnv) } }
  }

  private suspend fun checkManageableEnv(
    pyProject: PyProject,
  ): EnvCheckerResult = withBackgroundProgress(pyProject.project, PySdkBundle.message("python.sdk.validating.environment")) {
    val pipfile = pyProject.resolveFile(PIP_FILE)?.fileName ?: return@withBackgroundProgress EnvCheckerResult.CannotConfigure
    val pipEnvExecutable = pyProject.getEel().toFileSystem().resolveExecutable(PipEnvPyTool.getInstance())?.path
                           ?: return@withBackgroundProgress EnvCheckerResult.CannotConfigure
    val canManage = pipEnvExecutable.isExecutable()
    val intentionName = PipEnvBundle.message("sdk.create.pipenv.suggestion", pipfile)
    val envNotFound = EnvCheckerResult.EnvNotFound(intentionName)

    if (canManage) {
      val envPath = runPipEnv(
        pyProject.baseDir.asEelOrJustPath(),
        "--venv",
        transformer = ZeroCodeStdoutParserTransformer { PyResult.success(Path.of(it)) }
      ).successOrNull
      val path = envPath?.resolvePythonBinary()
      val envExists = path?.let {
        StandardFileSystems.local().refreshAndFindFileByPath(it.pathString) != null
      } ?: false
      if (envExists) {
        path.findEnvOrNull(intentionName) ?: envNotFound
      }
      else envNotFound
    }
    else EnvCheckerResult.CannotConfigure
  }

  /** Creates the pipenv environment. [baseEnv] places it, see [setupPipEnv]; without it pipenv chooses the place. */
  private suspend fun createAndAddSdk(pyProject: PyProject, baseEnv: List<EnvVar> = emptyList()): PyResult<PythonInterpreter> {
    LOGGER.debug("Creating pipenv environment")
    val project = pyProject.project
    return withBackgroundProgress(project, PipEnvBundle.message("python.sdk.using.pipenv.sentence")) {
      val basePath = pyProject.baseDir
      val pipEnv = setupPipEnv(basePath, null, true, baseEnv).getOr {
        PySdkConfigurationCollector.logPipEnv(project, PipEnvResult.CREATION_FAILURE)
        return@withBackgroundProgress it
      }

      val path = withContext(Dispatchers.IO) { VirtualEnvReader().findPythonInPythonRoot(pipEnv) }
      if (path == null) {
        return@withBackgroundProgress PyResult.localizedError(PySdkBundle.message("cannot.find.executable", "python", pipEnv))
      }

      val file = StandardFileSystems.local().refreshAndFindFileByPath(path.toString())
      if (file == null) {
        return@withBackgroundProgress PyResult.localizedError(PySdkBundle.message("cannot.find.executable", "python", path))
      }

      PySdkConfigurationCollector.logPipEnv(project, PipEnvResult.CREATED)
      LOGGER.debug("Setting up associated pipenv environment: $path, $basePath")

      val sdk = createSdk(
        pyProject,
        PathHolder.Eel(file.toNioPath()),
        PyPipEnvSdkAdditionalData(basePath),
        suggestedSdkName(basePath.pathString)
      ).getOr { return@withBackgroundProgress it }

      PyResult.success(sdk)
    }
  }
}
