// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.poetry.sdk.configuration

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.util.progress.reportRawProgress
import com.intellij.python.common.tools.ToolId
import com.intellij.python.community.impl.poetry.common.POETRY_TOOL_ID
import com.intellij.python.community.impl.poetry.common.poetryPath
import com.intellij.python.community.services.systemPython.SystemPythonService
import com.intellij.openapi.application.readAction
import com.intellij.openapi.vfs.findPsiFile
import com.intellij.python.pyproject.PyProjectToml
import com.intellij.python.pyproject.psi.resolvePythonVersionSpecifiers
import com.jetbrains.python.PyBundle
import com.jetbrains.python.packaging.PyVersionSpecifiers
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.poetry.findPoetryLock
import com.jetbrains.python.poetry.getPyProjectTomlForPoetry
import com.jetbrains.python.projectCreation.getSystemPython
import com.jetbrains.python.sdk.PythonSdkType
import com.jetbrains.python.sdk.baseDir
import com.jetbrains.python.sdk.configuration.CheckToml
import com.jetbrains.python.sdk.configuration.CreateSdkInfo
import com.jetbrains.python.sdk.configuration.EnvCheckerResult
import com.jetbrains.python.sdk.configuration.PyProjectTomlConfigurationExtension
import com.jetbrains.python.sdk.configuration.findEnvOrNull
import com.jetbrains.python.sdk.configuration.prepareSdkCreator
import com.jetbrains.python.sdk.impl.PySdkBundle
import com.jetbrains.python.sdk.impl.resolvePythonBinary
import com.jetbrains.python.sdk.legacy.PythonSdkUtil
import com.jetbrains.python.sdk.poetry.PyPoetrySdkAdditionalData
import com.jetbrains.python.sdk.poetry.getPoetryExecutable
import com.jetbrains.python.sdk.poetry.runPoetry
import com.jetbrains.python.sdk.poetry.setupPoetry
import com.jetbrains.python.sdk.poetry.suggestedSdkName
import com.jetbrains.python.sdk.service.PySdkService.Companion.pySdkService
import com.jetbrains.python.sdk.setAssociationToModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path
import kotlin.io.path.pathString

internal class PyPoetrySdkConfiguration : PyProjectTomlConfigurationExtension {

  companion object {
    private val LOGGER = Logger.getInstance(PyPoetrySdkConfiguration::class.java)
  }

  override val toolId: ToolId = POETRY_TOOL_ID

  override suspend fun checkEnvironmentAndPrepareSdkCreator(module: Module, venvsInModule: List<PythonBinary>): CreateSdkInfo? =
    prepareSdkCreator(
      { checkManageableEnv(module, true) },
    ) { { createPoetry(module) } }

  override suspend fun createSdkWithoutPyProjectTomlChecks(module: Module, venvsInModule: List<PythonBinary>): CreateSdkInfo? =
    prepareSdkCreator(
      { checkManageableEnv(module, false) },
    ) { { createPoetry(module) } }

  override fun asPyProjectTomlSdkConfigurationExtension(): PyProjectTomlConfigurationExtension = this

  private suspend fun checkManageableEnv(
    module: Module, checkToml: CheckToml,
  ): EnvCheckerResult = reportRawProgress {
    it.text(PyBundle.message("python.sdk.validating.environment"))
    val poetryLockExists = findPoetryLock(module) != null

    val isPoetryProject = if (checkToml) {
      withContext(Dispatchers.IO) {
        PyProjectToml.findFile(module)
          ?.let { toml -> getPyProjectTomlForPoetry(toml) } != null || poetryLockExists
      }
    }
    else true

    val canManage = isPoetryProject && getPoetryExecutable() != null
    val intentionName = PyBundle.message("sdk.set.up.poetry.environment")
    val envNotFound = EnvCheckerResult.EnvNotFound(intentionName)

    if (canManage) {
      val basePath = module.baseDir?.path?.toNioPathOrNull()
      runPoetry(basePath, "check", "--lock").getOr { return@reportRawProgress envNotFound }
      val envPath = runPoetry(basePath, "env", "info", "-p")
        .mapSuccess { it.toNioPathOrNull() }
        .getOr { return@reportRawProgress envNotFound }
      envPath?.resolvePythonBinary()?.findEnvOrNull(intentionName) ?: envNotFound
    }
    /**
     * We're confident that it's a poetry project in two cases:
     * - File poetry.lock exists
     * - We checked pyproject.toml and there's a specific mention of poetry tool
     */
    else if (poetryLockExists || (isPoetryProject && checkToml)) {
      val pathPersister: (Path) -> Unit = { path -> PropertiesComponent.getInstance().poetryPath = path.toString() }
      val toolName = "poetry"
      EnvCheckerResult.SuggestToolInstallation(
        toolToInstall = toolName,
        pathPersister = pathPersister,
        intentionName = PyBundle.message("sdk.create.custom.venv.install.fix.title.using.pip", "poetry")
      )
    }
    else EnvCheckerResult.CannotConfigure
  }

  private suspend fun createPoetry(module: Module): PyResult<Sdk> =
    withBackgroundProgress(module.project, PyBundle.message("sdk.progress.text.setting.up.poetry.environment")) {
      LOGGER.debug("Creating poetry environment")

      val basePath = module.baseDir?.path?.let { Path.of(it) }
      if (basePath == null) {
        return@withBackgroundProgress PyResult.localizedError(
          PyBundle.message(
            "python.sdk.provided.path.is.invalid",
            module.baseDir?.path
          )
        )
      }
      val tomlFile = PyProjectToml.findFile(module)
      val versionSpecifiers = tomlFile?.let { vf ->
        readAction { vf.findPsiFile(module.project) }?.resolvePythonVersionSpecifiers()
      } ?: PyVersionSpecifiers.ANY_SUPPORTED

      val baseSystemPython = getSystemPython(
        confirmInstallation = { true },
        pythonService = SystemPythonService(),
        versionSpecifiers = versionSpecifiers,
      ).getOr { return@withBackgroundProgress it }

      val poetry = setupPoetry(
        projectPath = basePath,
        basePythonBinaryPath = baseSystemPython.pythonBinary,
        installPackages = true,
        init = tomlFile == null
      ).getOr { return@withBackgroundProgress it }

      val path = poetry.resolvePythonBinary()
                 ?: return@withBackgroundProgress PyResult.localizedError(PySdkBundle.message("cannot.find.executable", "python", poetry))

      val file = LocalFileSystem.getInstance().refreshAndFindFileByPath(path.pathString)
                 ?: return@withBackgroundProgress PyResult.localizedError(PySdkBundle.message("cannot.find.executable", "python", path))

      LOGGER.debug("Setting up associated poetry environment: $path, $basePath")
      val sdk = SdkConfigurationUtil.setupSdk(
        PythonSdkUtil.getAllSdks().toTypedArray(),
        file,
        PythonSdkType.getInstance(),
        PyPoetrySdkAdditionalData(module.baseDir?.path?.let { Path.of(it) }),
        suggestedSdkName(basePath)
      )

      withContext(Dispatchers.EDT) {
        LOGGER.debug("Adding associated poetry environment: $path, $basePath")
        sdk.setAssociationToModule(module)
        SdkConfigurationUtil.addSdk(sdk)
        module.project.pySdkService.persistSdk(sdk)
      }

      PyResult.success(sdk)
    }
}
