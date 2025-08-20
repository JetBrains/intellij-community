// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.configuration

import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.util.progress.reportRawProgress
import com.intellij.pycharm.community.ide.impl.PyCharmCommunityCustomizationBundle
import com.intellij.python.pyproject.PyProjectToml
import com.jetbrains.python.PyBundle
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.poetry.findPoetryLock
import com.jetbrains.python.poetry.getPyProjectTomlForPoetry
import com.jetbrains.python.resolvePythonBinary
import com.jetbrains.python.sdk.PythonSdkType
import com.jetbrains.python.sdk.PythonSdkUtil
import com.jetbrains.python.sdk.basePath
import com.jetbrains.python.sdk.configuration.PyProjectSdkConfigurationExtension
import com.jetbrains.python.sdk.poetry.PyPoetrySdkAdditionalData
import com.jetbrains.python.sdk.poetry.getPoetryExecutable
import com.jetbrains.python.sdk.poetry.setupPoetry
import com.jetbrains.python.sdk.poetry.suggestedSdkName
import com.jetbrains.python.sdk.setAssociationToModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Path
import kotlin.io.path.pathString

internal class PyPoetrySdkConfiguration : PyProjectSdkConfigurationExtension {
  companion object {
    private val LOGGER = Logger.getInstance(PyPoetrySdkConfiguration::class.java)
  }

  @NlsSafe
  override suspend fun getIntention(module: Module): String? = reportRawProgress {
    it.text(PyBundle.message("python.sdk.validating.environment"))

    val isPoetryProject = withContext(Dispatchers.IO) {
      PyProjectToml.findFile(module)?.let { toml -> getPyProjectTomlForPoetry(toml) } != null ||
      findPoetryLock(module) != null
    }

    val isReadyToSetup = isPoetryProject && getPoetryExecutable().successOrNull != null

    return if (isReadyToSetup) PyCharmCommunityCustomizationBundle.message("sdk.set.up.poetry.environment") else null
  }


  override suspend fun createAndAddSdkForConfigurator(module: Module): PyResult<Sdk> = createPoetry(module)

  override suspend fun createAndAddSdkForInspection(module: Module): PyResult<Sdk> = createPoetry(module)

  override fun supportsHeadlessModel(): Boolean = true

  private suspend fun createPoetry(module: Module): PyResult<Sdk> =
    withBackgroundProgress(module.project, PyCharmCommunityCustomizationBundle.message("sdk.progress.text.setting.up.poetry.environment")) {
      LOGGER.debug("Creating poetry environment")

      val basePath = module.basePath?.let { Path.of(it) }
      if (basePath == null) {
        return@withBackgroundProgress PyResult.localizedError(PyBundle.message("python.sdk.provided.path.is.invalid", module.basePath))
      }
      val tomlFile = PyProjectToml.findFile(module)
      val poetry = setupPoetry(basePath, null, true, tomlFile == null).getOr { return@withBackgroundProgress it }
      val path = poetry.resolvePythonBinary()
                 ?: return@withBackgroundProgress PyResult.localizedError(PyBundle.message("cannot.find.executable", "python", poetry))

      val file = LocalFileSystem.getInstance().refreshAndFindFileByPath(path.pathString)
      if (file == null) {
        return@withBackgroundProgress PyResult.localizedError(PyBundle.message("cannot.find.executable", "python", path))
      }

      LOGGER.debug("Setting up associated poetry environment: $path, $basePath")
      val sdk = SdkConfigurationUtil.setupSdk(
        PythonSdkUtil.getAllSdks().toTypedArray(),
        file,
        PythonSdkType.getInstance(),
        PyPoetrySdkAdditionalData(module.basePath?.let { Path.of(it) }),
        suggestedSdkName(basePath)
      )

      withContext(Dispatchers.EDT) {
        LOGGER.debug("Adding associated poetry environment: ${path}, $basePath")
        sdk.setAssociationToModule(module)
        SdkConfigurationUtil.addSdk(sdk)
      }

      PyResult.success(sdk)
    }
}
