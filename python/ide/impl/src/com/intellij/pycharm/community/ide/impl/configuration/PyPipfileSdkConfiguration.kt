// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.configuration

import com.intellij.codeInspection.util.IntentionName
import com.intellij.execution.ExecutionException
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.jetbrains.python.PyBundle
import com.intellij.pycharm.community.ide.impl.PyCharmCommunityCustomizationBundle
import com.jetbrains.python.sdk.*
import com.intellij.pycharm.community.ide.impl.configuration.PySdkConfigurationCollector.InputData
import com.intellij.pycharm.community.ide.impl.configuration.PySdkConfigurationCollector.PipEnvResult
import com.intellij.pycharm.community.ide.impl.configuration.PySdkConfigurationCollector.Source
import com.jetbrains.python.sdk.configuration.PyProjectSdkConfigurationExtension
import com.jetbrains.python.sdk.pipenv.*
import java.awt.BorderLayout
import java.awt.Insets
import java.nio.file.Path
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.io.path.isExecutable

class PyPipfileSdkConfiguration : PyProjectSdkConfigurationExtension {

  private val LOGGER = Logger.getInstance(PyPipfileSdkConfiguration::class.java)

  override fun createAndAddSdkForConfigurator(module: Module): Sdk? = createAndAddSDk(module, Source.CONFIGURATOR)

  override fun getIntention(module: Module): @IntentionName  String? =
    module.pipFile?.let { PyCharmCommunityCustomizationBundle.message("sdk.create.pipenv.suggestion", it.name) }

  override fun createAndAddSdkForInspection(module: Module): Sdk? = createAndAddSDk(module, Source.INSPECTION)

  private fun createAndAddSDk(module: Module, source: Source): Sdk? {
    val pipEnvExecutable = askForEnvData(module, source) ?: return null
    PropertiesComponent.getInstance().pipEnvPath = pipEnvExecutable.pipEnvPath
    return createPipEnv(module)
  }

  private fun askForEnvData(module: Module, source: Source): PyAddNewPipEnvFromFilePanel.Data? {
    val pipEnvExecutable = getPipEnvExecutable()?.absolutePath

    if (source == Source.INSPECTION && pipEnvExecutable?.let { Path.of(it).isExecutable() } == true) {
      return PyAddNewPipEnvFromFilePanel.Data(pipEnvExecutable)
    }

    var permitted = false
    var envData: PyAddNewPipEnvFromFilePanel.Data? = null

    ApplicationManager.getApplication().invokeAndWait {
      val dialog = Dialog(module)

      permitted = dialog.showAndGet()
      envData = dialog.envData

      LOGGER.debug("Dialog exit code: ${dialog.exitCode}, $permitted")
    }

    PySdkConfigurationCollector.logPipEnvDialog(
      module.project,
      permitted,
      source,
      if (pipEnvExecutable.isNullOrBlank()) InputData.NOT_FILLED else InputData.SPECIFIED
    )
    return if (permitted) envData else null
  }

  private fun createPipEnv(module: Module): Sdk? {
    ProgressManager.progress(PyBundle.message("python.sdk.setting.up.pipenv.sentence"))
    LOGGER.debug("Creating pipenv environment")

    val basePath = module.basePath ?: return null
    val pipEnv = try {
      setupPipEnv(FileUtil.toSystemDependentName(basePath), null, true)
    }
    catch (e: ExecutionException) {
      PySdkConfigurationCollector.logPipEnv(module.project, PipEnvResult.CREATION_FAILURE)
      LOGGER.warn("Exception during creating pipenv environment", e)
      showSdkExecutionException(null, e, PyCharmCommunityCustomizationBundle.message("sdk.create.pipenv.exception.dialog.title"))
      return null
    }

    val path =  VirtualEnvReader.Instance.findPythonInPythonRoot(Path.of(pipEnv)).also {
      if (it == null) {
        PySdkConfigurationCollector.logPipEnv(module.project, PipEnvResult.NO_EXECUTABLE)
        LOGGER.warn("Python executable is not found: $pipEnv")
      }
    } ?: return null

    val file = LocalFileSystem.getInstance().refreshAndFindFileByPath(path.toString()).also {
      if (it == null) {
        PySdkConfigurationCollector.logPipEnv(module.project, PipEnvResult.NO_EXECUTABLE_FILE)
        LOGGER.warn("Python executable file is not found: $path")
      }
    } ?: return null

    PySdkConfigurationCollector.logPipEnv(module.project, PipEnvResult.CREATED)

    LOGGER.debug("Setting up associated pipenv environment: $path, $basePath")
    val sdk = SdkConfigurationUtil.setupSdk(
      ProjectJdkTable.getInstance().allJdks,
      file,
      PythonSdkType.getInstance(),
      null,
      suggestedSdkName(basePath)
    )

    ApplicationManager.getApplication().invokeAndWait {
      LOGGER.debug("Adding associated pipenv environment: $path, $basePath")
      sdk.isPipEnv = true
      sdk.setAssociationToModule(module)
      SdkConfigurationUtil.addSdk(sdk)
    }

    return sdk
  }

  private class Dialog(module: Module) : DialogWrapper(module.project, false, IdeModalityType.PROJECT) {

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
