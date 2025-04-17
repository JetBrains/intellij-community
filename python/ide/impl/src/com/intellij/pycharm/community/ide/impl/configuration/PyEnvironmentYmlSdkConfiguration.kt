// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.configuration

import com.intellij.codeInspection.util.IntentionName
import com.intellij.execution.ExecutionException
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pycharm.community.ide.impl.PyCharmCommunityCustomizationBundle
import com.intellij.pycharm.community.ide.impl.configuration.PySdkConfigurationCollector.CondaEnvResult
import com.intellij.pycharm.community.ide.impl.configuration.PySdkConfigurationCollector.InputData
import com.intellij.pycharm.community.ide.impl.configuration.PySdkConfigurationCollector.Source
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.components.JBLabel
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.ui.JBUI
import com.jetbrains.python.PyBundle
import com.jetbrains.python.configuration.PyConfigurableInterpreterList
import com.jetbrains.python.pathValidation.PlatformAndRoot
import com.jetbrains.python.psi.PyUtil
import com.jetbrains.python.run.PythonInterpreterTargetEnvironmentFactory
import com.jetbrains.python.sdk.PythonSdkUpdater
import com.jetbrains.python.sdk.add.v1.PyAddNewCondaEnvFromFilePanel
import com.jetbrains.python.sdk.basePath
import com.jetbrains.python.sdk.conda.PyCondaSdkCustomizer
import com.jetbrains.python.sdk.conda.createCondaSdkAlongWithNewEnv
import com.jetbrains.python.sdk.conda.suggestCondaPath
import com.jetbrains.python.sdk.configuration.PyProjectSdkConfigurationExtension
import com.jetbrains.python.sdk.flavors.conda.CondaEnvSdkFlavor
import com.jetbrains.python.sdk.flavors.conda.NewCondaEnvRequest
import com.jetbrains.python.sdk.flavors.conda.PyCondaCommand
import com.jetbrains.python.sdk.flavors.listCondaEnvironments
import com.jetbrains.python.sdk.setAssociationToModule
import com.jetbrains.python.sdk.showSdkExecutionException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.awt.BorderLayout
import java.awt.Insets
import java.nio.file.Path
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * This class only supports local, not remote target.
 * TODO: Support remote target (ie \\wsl)
 */
internal class PyEnvironmentYmlSdkConfiguration : PyProjectSdkConfigurationExtension {
  private val LOGGER = Logger.getInstance(PyEnvironmentYmlSdkConfiguration::class.java)
  @RequiresBackgroundThread
  override fun createAndAddSdkForConfigurator(module: Module): Sdk? = createAndAddSdk(module, Source.CONFIGURATOR)

  override fun getIntention(module: Module): @IntentionName String? = getEnvironmentYml(module)?.let {
    PyCharmCommunityCustomizationBundle.message("sdk.create.condaenv.suggestion")
  }
  @RequiresBackgroundThread
  override fun createAndAddSdkForInspection(module: Module): Sdk? = createAndAddSdk(module, Source.INSPECTION)

  private fun getEnvironmentYml(module: Module) = PyUtil.findInRoots(module, "environment.yml")

  @RequiresBackgroundThread
  private fun createAndAddSdk(module: Module, source: Source): Sdk? {
    val targetConfig = PythonInterpreterTargetEnvironmentFactory.getTargetModuleResidesOn(module)
    if (targetConfig != null) {
      // Remote targets aren't supported yet
      return null
    }

    val (condaExecutable, environmentYml) = askForEnvData(module, source) ?: return null
    return createAndAddCondaEnv(module, condaExecutable, environmentYml)?.also {
      PythonSdkUpdater.scheduleUpdate(it, module.project)
    }
  }

  @RequiresBackgroundThread
  private fun askForEnvData(module: Module, source: Source): PyAddNewCondaEnvFromFilePanel.Data? {
    val environmentYml = getEnvironmentYml(module) ?: return null
    // Again: only local conda is supported for now
    val condaExecutable = runBlocking { suggestCondaPath() }?.let { LocalFileSystem.getInstance().findFileByPath(it) }

    if (source == Source.INSPECTION && CondaEnvSdkFlavor.validateCondaPath(condaExecutable?.path, PlatformAndRoot.local) == null) {
      PySdkConfigurationCollector.logCondaEnvDialogSkipped(module.project, source, executableToEventField(condaExecutable?.path))
      return PyAddNewCondaEnvFromFilePanel.Data(condaExecutable!!.path, environmentYml.path)
    }

    var permitted = false
    var envData: PyAddNewCondaEnvFromFilePanel.Data? = null

    ApplicationManager.getApplication().invokeAndWait {
      val dialog = Dialog(module, condaExecutable, environmentYml)

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

    val sdk = createCondaEnv(module.project, condaExecutable, environmentYml) ?: return null
    PySdkConfigurationCollector.logCondaEnv(module.project, CondaEnvResult.CREATED)

    val shared = PyCondaSdkCustomizer.instance.sharedEnvironmentsByDefault
    val basePath = module.basePath

    ApplicationManager.getApplication().invokeAndWait {
      LOGGER.debug("Adding conda environment: ${sdk.homePath}, associated ${shared}}, module path ${basePath})")
      if (!shared) {
        sdk.setAssociationToModule(module)
      }

      SdkConfigurationUtil.addSdk(sdk)
    }

    return sdk
  }

  private fun executableToEventField(condaExecutable: String?): InputData {
    return if (condaExecutable.isNullOrBlank()) InputData.NOT_FILLED else InputData.SPECIFIED
  }

  private fun createCondaEnv(project: Project, condaExecutable: String, environmentYml: String): Sdk? {
    val condaEnvironmentsBefore = safelyListCondaEnvironments(project, condaExecutable) ?: return null

    val sdk = runBlocking {
      val existingSdks = PyConfigurableInterpreterList.getInstance(project).model.sdks
      val newCondaEnvInfo = NewCondaEnvRequest.LocalEnvByLocalEnvironmentFile(Path.of(environmentYml))
      PyCondaCommand(condaExecutable, null)
        .createCondaSdkAlongWithNewEnv(newCondaEnvInfo, Dispatchers.EDT, existingSdks.toList(), project)
    }.getOrElse { e ->
      if (e !is ExecutionException) throw e
      PySdkConfigurationCollector.logCondaEnv(project, CondaEnvResult.CREATION_FAILURE)
      LOGGER.warn("Exception during creating conda environment", e)
      showSdkExecutionException(null, e, PyCharmCommunityCustomizationBundle.message("sdk.create.condaenv.exception.dialog.title"))
      return null
    }

    val condaEnvironmentsAfter = safelyListCondaEnvironments(project, condaExecutable) ?: return null
    val difference = condaEnvironmentsAfter - condaEnvironmentsBefore.toSet()
    difference.singleOrNull().also {
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
    }
    return sdk

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

  private class Dialog(module: Module, condaBinary: VirtualFile?, environmentYml: VirtualFile) : DialogWrapper(module.project, false,
                                                                                                               IdeModalityType.PROJECT) {

    private val panel = PyAddNewCondaEnvFromFilePanel(module, condaBinary?.toNioPath(), environmentYml)

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
