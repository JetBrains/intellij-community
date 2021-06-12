// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk.configuration

import com.intellij.codeInspection.util.IntentionName
import com.intellij.execution.ExecutionException
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PyCharmCommunityCustomizationBundle
import com.jetbrains.python.packaging.PyCondaPackageService
import com.jetbrains.python.psi.PyUtil
import com.jetbrains.python.sdk.*
import com.jetbrains.python.sdk.add.PyAddNewCondaEnvFromFilePanel
import com.jetbrains.python.sdk.conda.PyCondaSdkCustomizer
import com.jetbrains.python.sdk.configuration.PySdkConfigurationCollector.Companion.CondaEnvResult
import com.jetbrains.python.sdk.configuration.PySdkConfigurationCollector.Companion.InputData
import com.jetbrains.python.sdk.configuration.PySdkConfigurationCollector.Companion.Source
import com.jetbrains.python.sdk.flavors.CondaEnvSdkFlavor
import com.jetbrains.python.sdk.flavors.VirtualEnvSdkFlavor
import com.jetbrains.python.sdk.flavors.listCondaEnvironments
import com.jetbrains.python.sdk.flavors.runConda
import java.awt.BorderLayout
import java.awt.Insets
import javax.swing.JComponent
import javax.swing.JPanel

class PyEnvironmentYmlSdkConfiguration : PyProjectSdkConfigurationExtension {

  private val LOGGER = Logger.getInstance(PyEnvironmentYmlSdkConfiguration::class.java)

  override fun createAndAddSdkForConfigurator(module: Module): Sdk? = createAndAddSdk(module, Source.CONFIGURATOR)

  override fun getIntention(module: Module): @IntentionName String? = getEnvironmentYml(module)?.let {
    PyCharmCommunityCustomizationBundle.message("sdk.create.condaenv.suggestion")
  }

  override fun createAndAddSdkForInspection(module: Module): Sdk? = createAndAddSdk(module, Source.INSPECTION)

  private fun getEnvironmentYml(module: Module) = PyUtil.findInRoots(module, "environment.yml")

  private fun createAndAddSdk(module: Module, source: Source): Sdk? {
    val (condaExecutable, environmentYml) = askForEnvData(module, source) ?: return null
    return createAndAddCondaEnv(module, condaExecutable, environmentYml)?.also { PyCondaPackageService.onCondaEnvCreated(condaExecutable) }
  }

  private fun askForEnvData(module: Module, source: Source): PyAddNewCondaEnvFromFilePanel.Data? {
    val environmentYml = getEnvironmentYml(module) ?: return null
    val condaExecutable = PyCondaPackageService.getCondaExecutable(null)

    if (source == Source.INSPECTION && CondaEnvSdkFlavor.validateCondaPath(condaExecutable) == null) {
      PySdkConfigurationCollector.logCondaEnvDialogSkipped(module.project, source, executableToEventField(condaExecutable))
      return PyAddNewCondaEnvFromFilePanel.Data(condaExecutable!!, environmentYml.path)
    }

    var permitted = false
    var envData: PyAddNewCondaEnvFromFilePanel.Data? = null

    ApplicationManager.getApplication().invokeAndWait {
      val dialog = Dialog(module, environmentYml)

      permitted = dialog.showAndGet()
      envData = dialog.envData

      LOGGER.debug("Dialog exit code: ${dialog.exitCode}, $permitted")
    }

    PySdkConfigurationCollector.logCondaEnvDialog(module.project, permitted, source, executableToEventField(envData?.condaPath))
    return if (permitted) envData else null
  }

  private fun createAndAddCondaEnv(module: Module, condaExecutable: String, environmentYml: String): Sdk? {
    ProgressManager.progress(PyBundle.message("python.sdk.creating.conda.environment.sentence"))
    LOGGER.debug("Creating conda environment")

    val path = createCondaEnv(module.project, condaExecutable, environmentYml) ?: return null
    PySdkConfigurationCollector.logCondaEnv(module.project, CondaEnvResult.CREATED)

    val shared = PyCondaSdkCustomizer.instance.sharedEnvironmentsByDefault
    val basePath = module.basePath

    val sdk = PyDetectedSdk(path).let {
      val existingSdks = ProjectJdkTable.getInstance().allJdks.asList()

      if (shared) {
        LOGGER.debug("Setting up conda environment: $path")
        it.setup(existingSdks)
      }
      else {
        LOGGER.debug("Setting up associated conda environment: $path, $basePath")
        it.setupAssociated(existingSdks, basePath)
      }
    } ?: return null

    ApplicationManager.getApplication().invokeAndWait {
      if (shared) {
        LOGGER.debug("Adding conda environment: $path")
      }
      else {
        LOGGER.debug("Adding associated conda environment: $path, $basePath")
      }

      SdkConfigurationUtil.addSdk(sdk)
      if (!shared) sdk.associateWithModule(module, null)
    }

    return sdk
  }

  private fun executableToEventField(condaExecutable: String?): InputData {
    return if (condaExecutable.isNullOrBlank()) InputData.NOT_FILLED else InputData.SPECIFIED
  }

  private fun createCondaEnv(project: Project, condaExecutable: String, environmentYml: String): String? {
    val condaEnvironmentsBefore = safelyListCondaEnvironments(project, condaExecutable) ?: return null

    try {
      runConda(condaExecutable, listOf("env", "create", "-f", environmentYml))
    }
    catch (e: ExecutionException) {
      PySdkConfigurationCollector.logCondaEnv(project, CondaEnvResult.CREATION_FAILURE)
      LOGGER.warn("Exception during creating conda environment", e)
      showSdkExecutionException(null, e, PyCharmCommunityCustomizationBundle.message("sdk.create.condaenv.exception.dialog.title"))
      return null
    }

    val condaEnvironmentsAfter = safelyListCondaEnvironments(project, condaExecutable) ?: return null
    val difference = condaEnvironmentsAfter - condaEnvironmentsBefore
    val rootDir = difference.singleOrNull().also {
      if (it == null) {
        PySdkConfigurationCollector.logCondaEnv(
          project,
          if (difference.isEmpty()) CondaEnvResult.NO_LISTING_DIFFERENCE else CondaEnvResult.AMBIGUOUS_LISTING_DIFFERENCE
        )

        LOGGER.warn(
          """
          Several or none conda envs found:
          Before: $condaEnvironmentsBefore
          After: $condaEnvironmentsAfter
          """.trimIndent()
        )
      }
    } ?: return null

    val paths = VirtualEnvSdkFlavor.findInRootDirectory(LocalFileSystem.getInstance().refreshAndFindFileByPath(rootDir))
    return paths.singleOrNull().also {
      PySdkConfigurationCollector.logCondaEnv(
        project,
        if (paths.isEmpty()) CondaEnvResult.NO_BINARY else CondaEnvResult.AMBIGUOUS_BINARIES
      )

      if (it == null) {
        LOGGER.warn("Several or none conda env binaries found: $rootDir, $paths")
      }
    }
  }

  private fun safelyListCondaEnvironments(project: Project, condaExecutable: String): List<String>? {
    return try {
      listCondaEnvironments(condaExecutable)
    }
    catch (e: ExecutionException) {
      PySdkConfigurationCollector.logCondaEnv(project, CondaEnvResult.LISTING_FAILURE)
      LOGGER.warn("Exception during listing conda environments", e)
      showSdkExecutionException(null, e, PyCharmCommunityCustomizationBundle.message("sdk.detect.condaenv.exception.dialog.title"))
      null
    }
  }

  private class Dialog(module: Module, environmentYml: VirtualFile) : DialogWrapper(module.project, false, IdeModalityType.PROJECT) {

    private val panel = PyAddNewCondaEnvFromFilePanel(module, environmentYml)

    val envData
      get() = panel.envData

    init {
      title = PyBundle.message("python.sdk.creating.conda.environment.title")
      init()
      Disposer.register(disposable) { if (isOK) panel.logData() }
    }

    override fun createCenterPanel(): JComponent {
      return JPanel(BorderLayout()).apply {
        val border = IdeBorderFactory.createEmptyBorder(Insets(4, 0, 6, 0))
        val message = PyCharmCommunityCustomizationBundle.message("sdk.create.condaenv.permission")

        add(
          JBUI.Panels.simplePanel(JBLabel(message)).withBorder(border),
          BorderLayout.NORTH
        )

        add(panel, BorderLayout.CENTER)
      }
    }

    override fun postponeValidation(): Boolean {
      return false
    }

    override fun doValidateAll(): List<ValidationInfo> = panel.validateAll()
  }
}
