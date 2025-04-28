// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.configuration

import com.intellij.CommonBundle
import com.intellij.codeInspection.util.IntentionName
import com.intellij.execution.ExecutionException
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.use
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pycharm.community.ide.impl.PyCharmCommunityCustomizationBundle
import com.intellij.pycharm.community.ide.impl.configuration.PySdkConfigurationCollector.InputData
import com.intellij.pycharm.community.ide.impl.configuration.PySdkConfigurationCollector.Source
import com.intellij.pycharm.community.ide.impl.configuration.PySdkConfigurationCollector.VirtualEnvResult
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.components.JBLabel
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.ui.JBUI
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PySdkBundle
import com.jetbrains.python.packaging.PyPackageManager
import com.jetbrains.python.packaging.PyPackageUtil
import com.jetbrains.python.packaging.PyTargetEnvironmentPackageManager
import com.jetbrains.python.requirements.RequirementsFileType
import com.jetbrains.python.sdk.add.v1.PyAddNewVirtualEnvFromFilePanel
import com.jetbrains.python.sdk.basePath
import com.jetbrains.python.sdk.configuration.PyProjectSdkConfigurationExtension
import com.jetbrains.python.sdk.configuration.createVirtualEnvAndSdkSynchronously
import com.jetbrains.python.sdk.isTargetBased
import com.jetbrains.python.sdk.showSdkExecutionException
import java.awt.BorderLayout
import java.awt.Insets
import java.nio.file.Paths
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.io.path.Path

private val LOGGER = fileLogger()

class PyRequirementsTxtOrSetupPySdkConfiguration : PyProjectSdkConfigurationExtension {
  @RequiresBackgroundThread
  override fun createAndAddSdkForConfigurator(module: Module): Sdk? = createAndAddSdk(module, Source.CONFIGURATOR).getOrLogException(LOGGER)

  override fun getIntention(module: Module): @IntentionName String? =
    getRequirementsTxtOrSetupPy(module)?.let { PyCharmCommunityCustomizationBundle.message("sdk.create.venv.suggestion", it.name) }

  @RequiresBackgroundThread
  override fun createAndAddSdkForInspection(module: Module): Sdk? = createAndAddSdk(module, Source.INSPECTION).getOrLogException(LOGGER)

  private fun createAndAddSdk(module: Module, source: Source): Result<Sdk> {
    val existingSdks = ProjectJdkTable.getInstance().allJdks.asList()

    val data = askForEnvData(module, existingSdks, source)
    if (data == null) {
      return com.jetbrains.python.failure("askForEnvData is null")
    }

    val (location, chosenBaseSdk, requirementsTxtOrSetupPy) = data
    val systemIndependentLocation = Path(location)
    val projectPath = module.basePath ?: module.project.basePath

    ProgressManager.progress(PySdkBundle.message("python.creating.venv.sentence"))

    try {
      val sdk = invokeAndWaitIfNeeded {
        Disposer.newDisposable("Creating virtual environment").use {
          PyTemporarilyIgnoredFileProvider.ignoreRoot(systemIndependentLocation, it)
          createVirtualEnvAndSdkSynchronously(chosenBaseSdk!!, existingSdks, location, projectPath, module.project, module)
        }
      }

      invokeAndWaitIfNeeded {
        thisLogger().debug("Adding associated virtual environment: ${sdk.homePath}, ${module.basePath}")
        SdkConfigurationUtil.addSdk(sdk)
      }

      val requirementsTxtOrSetupPyFile = VfsUtil.findFile(Paths.get(requirementsTxtOrSetupPy), false)
      if (requirementsTxtOrSetupPyFile == null) {
        PySdkConfigurationCollector.logVirtualEnv(module.project, VirtualEnvResult.DEPS_NOT_FOUND)
        thisLogger().warn("File with dependencies is not found: $requirementsTxtOrSetupPy")
        return Result.success(sdk)
      }

      thisLogger().debug("Installing packages")
      ProgressManager.progress(PyBundle.message("python.packaging.installing.packages"))
      val basePath = module.basePath

      val packageManager = PyPackageManager.getInstance(sdk)
      val command = getCommandForPipInstall(requirementsTxtOrSetupPyFile)

      // FIXME: lame cast...
      if (!sdk.isTargetBased() && packageManager is PyTargetEnvironmentPackageManager) {
        packageManager.install(emptyList(), command, basePath)
      }
      else {
        // TODO: double check installing over remote target
        packageManager.install(emptyList(), command)
      }

      return Result.success(sdk)
    }
    catch (e: ExecutionException) {
      PySdkConfigurationCollector.logVirtualEnv(module.project, VirtualEnvResult.INSTALLATION_FAILURE)
      showSdkExecutionException(chosenBaseSdk, e, PyBundle.message("python.packaging.failed.to.install.packages.title"))

      return Result.failure(e)
    }
  }

  private fun getRequirementsTxtOrSetupPy(module: Module) =
    PyPackageUtil.findRequirementsTxt(module) ?: PyPackageUtil.findSetupPy(module)?.virtualFile

  private fun askForEnvData(module: Module, existingSdks: List<Sdk>, source: Source): PyAddNewVirtualEnvFromFilePanel.Data? {
    val requirementsTxtOrSetupPy = getRequirementsTxtOrSetupPy(module) ?: return null

    var permitted = false
    var envData: PyAddNewVirtualEnvFromFilePanel.Data? = null

    ApplicationManager.getApplication().invokeAndWait {
      val dialog = Dialog(module, existingSdks, requirementsTxtOrSetupPy)

      permitted = dialog.showAndGet()
      envData = dialog.envData

      thisLogger().debug("Dialog exit code: ${dialog.exitCode}, $permitted")
    }

    PySdkConfigurationCollector.logVirtualEnvDialog(
      module.project,
      permitted,
      source,
      if (envData?.baseSdk == null) InputData.NOT_FILLED else InputData.SPECIFIED
    )

    return if (permitted) envData else null
  }

  private fun getCommandForPipInstall(requirementsTxtOrSetupPy: VirtualFile): List<String> {
    return if (FileTypeRegistry.getInstance().isFileOfType(requirementsTxtOrSetupPy, RequirementsFileType.INSTANCE)) {
      listOf("-r", getAbsPath(requirementsTxtOrSetupPy))
    }
    else {
      listOf("-e", getAbsPath(requirementsTxtOrSetupPy.parent))
    }
  }

  @NlsSafe
  private fun getAbsPath(file: VirtualFile): String = file.toNioPath().toAbsolutePath().toString()

  private class Dialog(
    module: Module,
    existingSdks: List<Sdk>,
    private val requirementsTxtOrSetupPy: VirtualFile,
  ) : DialogWrapper(module.project, false, IdeModalityType.IDE) {

    private val panel = PyAddNewVirtualEnvFromFilePanel(module, existingSdks, requirementsTxtOrSetupPy)

    val envData
      get() = panel.envData

    init {
      title = PySdkBundle.message("python.creating.venv.title")
      init()
      Disposer.register(disposable) { if (isOK) panel.logData() }
    }

    override fun createCenterPanel(): JComponent {
      return JPanel(BorderLayout()).apply {
        val border = IdeBorderFactory.createEmptyBorder(JBUI.insets(4, 0, 6, 0))
        val message = PyCharmCommunityCustomizationBundle.message("sdk.create.venv.permission", requirementsTxtOrSetupPy.name)

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

    override fun doValidateAll(): List<ValidationInfo> = panel.validateAll(CommonBundle.getOkButtonText())
  }
}
