// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.configuration

import com.intellij.codeInspection.util.IntentionName
import com.intellij.ide.util.PropertiesComponent
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
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.jetbrains.python.PyBundle
import com.intellij.pycharm.community.ide.impl.PyCharmCommunityCustomizationBundle
import com.jetbrains.python.sdk.*
import com.intellij.pycharm.community.ide.impl.configuration.PySdkConfigurationCollector.InputData
import com.intellij.pycharm.community.ide.impl.configuration.PySdkConfigurationCollector.PipEnvResult
import com.intellij.pycharm.community.ide.impl.configuration.PySdkConfigurationCollector.Source
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.jetbrains.python.sdk.configuration.PyProjectSdkConfigurationExtension
import com.jetbrains.python.sdk.pipenv.*
import com.jetbrains.python.sdk.pipenv.ui.PyAddNewPipEnvFromFilePanel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import java.awt.Insets
import java.io.FileNotFoundException
import java.nio.file.Path
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.io.path.isExecutable
import kotlin.io.path.pathString

class PyPipfileSdkConfiguration : PyProjectSdkConfigurationExtension {

  private val LOGGER = Logger.getInstance(PyPipfileSdkConfiguration::class.java)

  @RequiresBackgroundThread
  override fun createAndAddSdkForConfigurator(module: Module): Sdk? = runBlockingCancellable { createAndAddSDk(module, Source.CONFIGURATOR) }

  @RequiresBackgroundThread
  override fun getIntention(module: Module): @IntentionName String? = findAmongRoots(module, PIP_FILE)?.let { PyCharmCommunityCustomizationBundle.message("sdk.create.pipenv.suggestion", it.name) }

  @RequiresBackgroundThread
  override fun createAndAddSdkForInspection(module: Module): Sdk? = runBlockingCancellable { createAndAddSDk(module, Source.INSPECTION) }

  private suspend fun createAndAddSDk(module: Module, source: Source): Sdk? {
    val pipEnvExecutable = askForEnvData(module, source) ?: return null
    PropertiesComponent.getInstance().pipEnvPath = pipEnvExecutable.pipEnvPath.pathString
    return createPipEnv(module).getOrNull()
  }

  private suspend fun askForEnvData(module: Module, source: Source): PyAddNewPipEnvFromFilePanel.Data? {
    val pipEnvExecutable = getPipEnvExecutable().getOrNull()

    if (source == Source.INSPECTION && pipEnvExecutable?.isExecutable() == true) {
      return PyAddNewPipEnvFromFilePanel.Data(pipEnvExecutable)
    }

    var permitted = false
    var envData: PyAddNewPipEnvFromFilePanel.Data? = null

    withContext(Dispatchers.EDT) {
      val dialog = Dialog(module)

      permitted = dialog.showAndGet()
      envData = dialog.envData

      LOGGER.debug("Dialog exit code: ${dialog.exitCode}, $permitted")
    }

    PySdkConfigurationCollector.logPipEnvDialog(
      module.project,
      permitted,
      source,
      if (pipEnvExecutable == null) InputData.NOT_FILLED else InputData.SPECIFIED
    )
    return if (permitted) envData else null
  }

  private suspend fun createPipEnv(module: Module): Result<Sdk> {
    LOGGER.debug("Creating pipenv environment")
    return withBackgroundProgress(module.project, PyBundle.message("python.sdk.setting.up.pipenv.sentence")) {
      val basePath = module.basePath ?: return@withBackgroundProgress Result.failure(FileNotFoundException("Can't find module base path"))
      val pipEnv = setupPipEnv(Path.of(basePath), null, true).getOrElse {
        PySdkConfigurationCollector.logPipEnv(module.project, PipEnvResult.CREATION_FAILURE)
        LOGGER.warn("Exception during creating pipenv environment", it)
        return@withBackgroundProgress Result.failure(it)
      }

      val path = withContext(Dispatchers.IO) { VirtualEnvReader.Instance.findPythonInPythonRoot(Path.of(pipEnv)) }
      if (path == null) {
        LOGGER.warn("Python executable is not found: $pipEnv")
        return@withBackgroundProgress Result.failure(FileNotFoundException("Python executable is not found: $pipEnv"))
      }

      val file = LocalFileSystem.getInstance().refreshAndFindFileByPath(path.toString())
      if (file == null) {
        LOGGER.warn("Python executable file is not found: $path")
        return@withBackgroundProgress Result.failure(FileNotFoundException("Python executable file is not found: $path"))
      }

      PySdkConfigurationCollector.logPipEnv(module.project, PipEnvResult.CREATED)
      LOGGER.debug("Setting up associated pipenv environment: $path, $basePath")

      val sdk = SdkConfigurationUtil.setupSdk(
        ProjectJdkTable.getInstance().allJdks,
        file,
        PythonSdkType.getInstance(),
        PyPipEnvSdkAdditionalData(),
        suggestedSdkName(basePath)
      )

      withContext(Dispatchers.EDT) {
        LOGGER.debug("Adding associated pipenv environment: $path, $basePath")
        sdk.setAssociationToModule(module)
        SdkConfigurationUtil.addSdk(sdk)
      }

      Result.success(sdk)
    }
  }

  internal class Dialog(module: Module) : DialogWrapper(module.project, false, IdeModalityType.PROJECT) {

    private val panel = PyAddNewPipEnvFromFilePanel(module)

    val envData
      get() = panel.envData

    init {
      title = PyBundle.message("python.sdk.setting.up.pipenv.title")
      init()
    }

    override fun createCenterPanel(): JComponent {
      return JPanel(BorderLayout()).apply {
        val border = IdeBorderFactory.createEmptyBorder(Insets(4, 0, 6, 0))
        val message = PyCharmCommunityCustomizationBundle.message("sdk.create.pipenv.permission")

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
