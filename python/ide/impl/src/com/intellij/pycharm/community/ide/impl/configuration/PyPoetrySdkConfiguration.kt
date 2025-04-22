// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.configuration

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.pycharm.community.ide.impl.PyCharmCommunityCustomizationBundle
import com.intellij.python.community.impl.poetry.poetryPath
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.components.JBLabel
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.ui.JBUI
import com.jetbrains.python.PyBundle
import com.jetbrains.python.sdk.PythonSdkType
import com.jetbrains.python.sdk.basePath
import com.jetbrains.python.sdk.configuration.PyProjectSdkConfigurationExtension
import com.jetbrains.python.sdk.findAmongRoots
import com.jetbrains.python.sdk.poetry.*
import com.jetbrains.python.sdk.poetry.ui.PyAddNewPoetryFromFilePanel
import com.jetbrains.python.sdk.setAssociationToModuleAsync
import com.jetbrains.python.util.runWithModalBlockingOrInBackground
import com.jetbrains.python.venvReader.VirtualEnvReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import java.io.FileNotFoundException
import java.nio.file.Path
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.io.path.pathString

internal class PyPoetrySdkConfiguration : PyProjectSdkConfigurationExtension {
  companion object {
    private val LOGGER = Logger.getInstance(PyPoetrySdkConfiguration::class.java)
  }

  override fun getIntention(module: Module): String? =
    runWithModalBlockingOrInBackground(module.project, PyBundle.message("python.sdk.validating.environment")) {
      val toml = findAmongRoots(module, PY_PROJECT_TOML)
      if (toml == null) {
        return@runWithModalBlockingOrInBackground null
      }

      val isPoetry = getPyProjectTomlForPoetry(toml) != null
      if (!isPoetry) {
        return@runWithModalBlockingOrInBackground null
      }

      return@runWithModalBlockingOrInBackground PyCharmCommunityCustomizationBundle.message("sdk.set.up.poetry.environment", toml.name)
    }

  @RequiresBackgroundThread
  override fun createAndAddSdkForConfigurator(module: Module): Sdk? = runBlockingCancellable { createAndAddSDk(module, false) }

  @RequiresBackgroundThread
  override fun createAndAddSdkForInspection(module: Module): Sdk? = runBlockingCancellable { createAndAddSDk(module, true) }

  override fun supportsHeadlessModel(): Boolean = true

  private suspend fun createAndAddSDk(module: Module, inspection: Boolean): Sdk? {
    val poetryEnvExecutable = askForEnvData(module, inspection) ?: return null
    PropertiesComponent.getInstance().poetryPath = poetryEnvExecutable.poetryPath.pathString
    return createPoetry(module).getOrNull()
  }

  private suspend fun askForEnvData(module: Module, inspection: Boolean): PyAddNewPoetryFromFilePanel.Data? {
    val poetryExecutable = getPoetryExecutable().getOrNull()
    val isHeadlessEnv = ApplicationManager.getApplication().isHeadlessEnvironment

    if ((inspection || isHeadlessEnv) && validatePoetryExecutable(poetryExecutable) == null) {
      return PyAddNewPoetryFromFilePanel.Data(poetryExecutable!!)
    }
    else if (isHeadlessEnv) {
      return null
    }

    var permitted = false
    var envData: PyAddNewPoetryFromFilePanel.Data? = null

    withContext(Dispatchers.EDT) {
      val dialog = Dialog(module)

      permitted = dialog.showAndGet()
      envData = dialog.envData

      LOGGER.debug("Dialog exit code: ${dialog.exitCode}, $permitted")
    }

    return if (permitted) envData else null
  }

  private suspend fun createPoetry(module: Module): Result<Sdk> =
    withBackgroundProgress(module.project, PyCharmCommunityCustomizationBundle.message("sdk.progress.text.setting.up.poetry.environment")) {
      LOGGER.debug("Creating poetry environment")

      val basePath = module.basePath?.let { Path.of(it) }
      if (basePath == null) {
        return@withBackgroundProgress Result.failure(FileNotFoundException("Can't find module base path"))
      }

      val poetry = setupPoetry(basePath, null, true, findAmongRoots(module, PY_PROJECT_TOML) == null).onFailure { return@withBackgroundProgress Result.failure(it) }.getOrThrow()

      val path = withContext(Dispatchers.IO) { VirtualEnvReader.Instance.findPythonInPythonRoot(Path.of(poetry)) }
      if (path == null) {
        return@withBackgroundProgress Result.failure(FileNotFoundException("Can't find python executable in $poetry"))
      }

      val file = LocalFileSystem.getInstance().refreshAndFindFileByPath(path.pathString)
      if (file == null) {
        return@withBackgroundProgress Result.failure(FileNotFoundException("Can't find python executable in $poetry"))
      }

      LOGGER.debug("Setting up associated poetry environment: $path, $basePath")
      val sdk = SdkConfigurationUtil.setupSdk(
        ProjectJdkTable.getInstance().allJdks,
        file,
        PythonSdkType.getInstance(),
        PyPoetrySdkAdditionalData(module.basePath?.let { Path.of(it) }),
        suggestedSdkName(basePath)
      )

      withContext(Dispatchers.EDT) {
        LOGGER.debug("Adding associated poetry environment: ${path}, $basePath")
        sdk.setAssociationToModuleAsync(module)
        SdkConfigurationUtil.addSdk(sdk)
      }

      Result.success(sdk)
    }


  private class Dialog(module: Module) : DialogWrapper(module.project, false, IdeModalityType.IDE) {

    private val panel = PyAddNewPoetryFromFilePanel(module)

    val envData
      get() = panel.envData

    init {
      title = PyCharmCommunityCustomizationBundle.message("sdk.dialog.title.setting.up.poetry.environment")
      init()
    }

    override fun createCenterPanel(): JComponent {
      return JPanel(BorderLayout()).apply {
        val border = IdeBorderFactory.createEmptyBorder(JBUI.insets(4, 0, 6, 0))
        val message = PyCharmCommunityCustomizationBundle.message("sdk.notification.label.set.up.poetry.environment.from.pyproject.toml.dependencies")

        add(
          JBUI.Panels.simplePanel(JBLabel(message)).withBorder(border),
          BorderLayout.NORTH
        )

        add(panel, BorderLayout.CENTER)
      }
    }

    override fun postponeValidation(): Boolean = false

    override fun doValidateAll(): List<ValidationInfo> = panel.validateAll()
  }
}
