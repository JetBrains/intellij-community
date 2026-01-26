// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.configuration

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.pycharm.community.ide.impl.PyCharmCommunityCustomizationBundle
import com.intellij.pycharm.community.ide.impl.configuration.PySdkConfigurationCollector.PipEnvResult
import com.intellij.pycharm.community.ide.impl.findEnvOrNull
import com.intellij.python.common.tools.ToolId
import com.intellij.python.community.execService.ZeroCodeStdoutParserTransformer
import com.intellij.python.community.impl.pipenv.pipenvPath
import com.jetbrains.python.PyBundle
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.sdk.PythonSdkType
import com.jetbrains.python.sdk.basePath
import com.jetbrains.python.sdk.configuration.*
import com.jetbrains.python.sdk.findAmongRoots
import com.jetbrains.python.sdk.impl.PySdkBundle
import com.jetbrains.python.sdk.impl.resolvePythonBinary
import com.jetbrains.python.sdk.legacy.PythonSdkUtil
import com.jetbrains.python.sdk.pipenv.*
import com.jetbrains.python.sdk.service.PySdkService.Companion.pySdkService
import com.jetbrains.python.sdk.setAssociationToModule
import com.jetbrains.python.venvReader.VirtualEnvReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path
import kotlin.io.path.isExecutable
import kotlin.io.path.pathString

private val LOGGER = Logger.getInstance(PyPipfileSdkConfiguration::class.java)

internal class PyPipfileSdkConfiguration : PyProjectSdkConfigurationExtension {

  override val toolId: ToolId = PIPENV_TOOL_ID

  override suspend fun checkEnvironmentAndPrepareSdkCreator(module: Module): CreateSdkInfo? = prepareSdkCreator(
    { checkManageableEnv(module, it) }
  ) { { createAndAddSdk(module) } }

  override fun asPyProjectTomlSdkConfigurationExtension(): PyProjectTomlConfigurationExtension? = null

  private suspend fun checkManageableEnv(
    module: Module, checkExistence: CheckExistence,
  ): EnvCheckerResult = withBackgroundProgress(module.project, PyBundle.message("python.sdk.validating.environment")) {
    val pipfile = findAmongRoots(module, PipEnvFileHelper.PIP_FILE)?.name ?: return@withBackgroundProgress EnvCheckerResult.CannotConfigure
    val pipEnvExecutable = getPipEnvExecutable() ?: return@withBackgroundProgress EnvCheckerResult.CannotConfigure
    val canManage = pipEnvExecutable.isExecutable()
    val intentionName = PyCharmCommunityCustomizationBundle.message("sdk.create.pipenv.suggestion", pipfile)
    val envNotFound = EnvCheckerResult.EnvNotFound(intentionName)

    when {
      canManage && checkExistence -> {
        PropertiesComponent.getInstance().pipenvPath = pipEnvExecutable.pathString
        val envPath = runPipEnv(
          module.basePath?.toNioPathOrNull(),
          "--venv",
          transformer = ZeroCodeStdoutParserTransformer { PyResult.success(Path.of(it)) }
        ).successOrNull
        val path = envPath?.resolvePythonBinary()
        val envExists = path?.let {
          LocalFileSystem.getInstance().refreshAndFindFileByPath(it.pathString) != null
        } ?: false
        if (envExists) {
          path.findEnvOrNull(intentionName) ?: envNotFound
        }
        else envNotFound
      }
      canManage -> envNotFound
      else -> EnvCheckerResult.CannotConfigure
    }
  }

  private suspend fun createAndAddSdk(module: Module): PyResult<Sdk> {
    LOGGER.debug("Creating pipenv environment")
    return withBackgroundProgress(module.project, PyBundle.message("python.sdk.using.pipenv.sentence")) {
      val basePath = module.basePath
                     ?: return@withBackgroundProgress PyResult.localizedError(PyBundle.message("python.sdk.provided.path.is.invalid",
                                                                                               module.basePath))
      val pipEnv = setupPipEnv(Path.of(basePath), null, true).getOr {
        PySdkConfigurationCollector.logPipEnv(module.project, PipEnvResult.CREATION_FAILURE)
        return@withBackgroundProgress it
      }

      val path = withContext(Dispatchers.IO) { VirtualEnvReader().findPythonInPythonRoot(Path.of(pipEnv)) }
      if (path == null) {
        return@withBackgroundProgress PyResult.localizedError(PySdkBundle.message("cannot.find.executable", "python", pipEnv))
      }

      val file = LocalFileSystem.getInstance().refreshAndFindFileByPath(path.toString())
      if (file == null) {
        return@withBackgroundProgress PyResult.localizedError(PySdkBundle.message("cannot.find.executable", "python", path))
      }

      PySdkConfigurationCollector.logPipEnv(module.project, PipEnvResult.CREATED)
      LOGGER.debug("Setting up associated pipenv environment: $path, $basePath")

      val sdk = SdkConfigurationUtil.setupSdk(
        PythonSdkUtil.getAllSdks().toTypedArray(),
        file,
        PythonSdkType.getInstance(),
        PyPipEnvSdkAdditionalData(),
        suggestedSdkName(basePath)
      )

      withContext(Dispatchers.EDT) {
        LOGGER.debug("Adding associated pipenv environment: $path, $basePath")
        sdk.setAssociationToModule(module)
        SdkConfigurationUtil.addSdk(sdk)
        module.project.pySdkService.persistSdk(sdk)
      }

      PyResult.success(sdk)
    }
  }
}
