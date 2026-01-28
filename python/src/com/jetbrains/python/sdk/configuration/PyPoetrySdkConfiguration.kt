// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.configuration

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
import com.intellij.python.pyproject.PyProjectToml
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PythonBinary
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.poetry.findPoetryLock
import com.jetbrains.python.poetry.getPyProjectTomlForPoetry
import com.jetbrains.python.sdk.PythonSdkType
import com.jetbrains.python.sdk.baseDir
import com.jetbrains.python.sdk.impl.PySdkBundle
import com.jetbrains.python.sdk.impl.resolvePythonBinary
import com.jetbrains.python.sdk.legacy.PythonSdkUtil
import com.jetbrains.python.sdk.poetry.*
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
      { checkExistence -> checkManageableEnv(module, checkExistence, true) },
    ) { { createPoetry(module) } }

  override suspend fun createSdkWithoutPyProjectTomlChecks(module: Module, venvsInModule: List<PythonBinary>): CreateSdkInfo? =
    prepareSdkCreator(
      { checkExistence -> checkManageableEnv(module, checkExistence, false) },
    ) { { createPoetry(module) } }

  override fun asPyProjectTomlSdkConfigurationExtension(): PyProjectTomlConfigurationExtension = this

  private suspend fun checkManageableEnv(
    module: Module, checkExistence: CheckExistence, checkToml: CheckToml,
  ): EnvCheckerResult = reportRawProgress {
    it.text(PyBundle.message("python.sdk.validating.environment"))

    val isPoetryProject = if (checkToml) {
      withContext(Dispatchers.IO) {
        PyProjectToml.findFile(module)?.let { toml -> getPyProjectTomlForPoetry(toml) } != null || findPoetryLock(module) != null
      }
    }
    else true

    val canManage = isPoetryProject && getPoetryExecutable() != null
    val intentionName = PyBundle.message("sdk.set.up.poetry.environment")
    val envNotFound = EnvCheckerResult.EnvNotFound(intentionName)

    when {
      canManage && checkExistence -> {
        val basePath = module.baseDir?.path?.toNioPathOrNull()
        runPoetry(basePath, "check", "--lock").getOr { return@reportRawProgress envNotFound }
        val envPath = runPoetry(basePath, "env", "info", "-p")
          .mapSuccess { it.toNioPathOrNull() }
          .getOr { return@reportRawProgress envNotFound }
        envPath?.resolvePythonBinary()?.findEnvOrNull(intentionName) ?: return@reportRawProgress envNotFound
      }
      canManage -> envNotFound
      else -> EnvCheckerResult.CannotConfigure
    }
  }

  private suspend fun createPoetry(module: Module): PyResult<Sdk> =
    withBackgroundProgress(module.project, PyBundle.message("sdk.progress.text.setting.up.poetry.environment")) {
      LOGGER.debug("Creating poetry environment")

      val basePath = module.baseDir?.path?.let { Path.of(it) }
      if (basePath == null) {
        return@withBackgroundProgress PyResult.localizedError(PyBundle.message("python.sdk.provided.path.is.invalid", module.baseDir?.path))
      }
      val tomlFile = PyProjectToml.findFile(module)
      val poetry = setupPoetry(basePath, null, true, tomlFile == null).getOr { return@withBackgroundProgress it }
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
