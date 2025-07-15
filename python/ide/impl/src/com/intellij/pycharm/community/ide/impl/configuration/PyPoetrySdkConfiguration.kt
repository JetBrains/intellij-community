// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.configuration

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.util.progress.reportRawProgress
import com.intellij.pycharm.community.ide.impl.PyCharmCommunityCustomizationBundle
import com.intellij.python.community.impl.poetry.poetryPath
import com.intellij.python.pyproject.PyProjectToml
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.jetbrains.python.PyBundle
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.getOrLogException
import com.jetbrains.python.sdk.PythonSdkType
import com.jetbrains.python.sdk.PythonSdkUtil
import com.jetbrains.python.sdk.basePath
import com.jetbrains.python.sdk.configuration.PyProjectSdkConfigurationExtension
import com.jetbrains.python.sdk.poetry.*
import com.jetbrains.python.sdk.poetry.ui.PyAddNewPoetryFromFilePanel
import com.jetbrains.python.sdk.setAssociationToModule
import com.jetbrains.python.venvReader.VirtualEnvReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import java.nio.file.Path
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.io.path.pathString

internal class PyPoetrySdkConfiguration : PyProjectSdkConfigurationExtension {
  companion object {
    private val LOGGER = Logger.getInstance(PyPoetrySdkConfiguration::class.java)
  }

  @NlsSafe
  override suspend fun getIntention(module: Module): String? = reportRawProgress {
    it.text(PyBundle.message("python.sdk.validating.environment"))
    val toml = PyProjectToml.findFile(module)
    if (toml == null) {
      return@reportRawProgress null
    }

    val isPoetry = getPyProjectTomlForPoetry(toml) != null
    if (!isPoetry) {
      return@reportRawProgress null
    }

    return@reportRawProgress PyCharmCommunityCustomizationBundle.message("sdk.set.up.poetry.environment", toml.name)
  }


  override suspend fun createAndAddSdkForConfigurator(module: Module): Sdk? = createAndAddSDk(module, false)

  override suspend fun createAndAddSdkForInspection(module: Module): Sdk? = createAndAddSDk(module, true)

  override fun supportsHeadlessModel(): Boolean = true

  private suspend fun createAndAddSDk(module: Module, inspection: Boolean): Sdk? {
    val poetryEnvExecutable = askForEnvData(module, inspection) ?: return null
    PropertiesComponent.getInstance().poetryPath = poetryEnvExecutable.poetryPath.pathString
    return createPoetry(module).getOrLogException(LOGGER)
  }

  private suspend fun askForEnvData(module: Module, inspection: Boolean): PyAddNewPoetryFromFilePanel.Data? {
    val poetryExecutable = getPoetryExecutable().getOrLogException(LOGGER)
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

  private suspend fun createPoetry(module: Module): PyResult<Sdk> =
    withBackgroundProgress(module.project, PyCharmCommunityCustomizationBundle.message("sdk.progress.text.setting.up.poetry.environment")) {
      LOGGER.debug("Creating poetry environment")

      val basePath = module.basePath?.let { Path.of(it) }
      if (basePath == null) {
        return@withBackgroundProgress PyResult.localizedError(PyBundle.message("python.sdk.provided.path.is.invalid",module.basePath))
      }
      val tomlFile = PyProjectToml.findFile(module)
      val poetry = setupPoetry(basePath, null, true, tomlFile == null).getOr { return@withBackgroundProgress it }

      val path = withContext(Dispatchers.IO) { VirtualEnvReader.Instance.findPythonInPythonRoot(Path.of(poetry)) }
      if (path == null) {
        return@withBackgroundProgress PyResult.localizedError(PyBundle.message("cannot.find.executable","python", poetry))
      }

      val file = LocalFileSystem.getInstance().refreshAndFindFileByPath(path.pathString)
      if (file == null) {
        return@withBackgroundProgress PyResult.localizedError(PyBundle.message("cannot.find.executable","python", path))
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
