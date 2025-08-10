// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.configuration

import com.intellij.CommonBundle
import com.intellij.codeInspection.util.IntentionName
import com.intellij.execution.ExecutionException
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.module.Module
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
import com.intellij.pycharm.community.ide.impl.configuration.ui.PyAddNewVirtualEnvFromFilePanel
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.jetbrains.python.PySdkBundle
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.packaging.PyPackageUtil
import com.jetbrains.python.packaging.management.PythonPackageManager
import com.jetbrains.python.packaging.requirementsTxt.PythonRequirementTxtSdkUtils
import com.jetbrains.python.packaging.setupPy.SetupPyManager
import com.jetbrains.python.requirements.RequirementsFileType
import com.jetbrains.python.sdk.PythonSdkUtil
import com.jetbrains.python.sdk.basePath
import com.jetbrains.python.sdk.configuration.PyProjectSdkConfigurationExtension
import com.jetbrains.python.sdk.configuration.createVirtualEnvAndSdkSynchronously
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import java.nio.file.Paths
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.io.path.Path

private val LOGGER = fileLogger()

class PyRequirementsTxtOrSetupPySdkConfiguration : PyProjectSdkConfigurationExtension {
  override suspend fun createAndAddSdkForConfigurator(module: Module): PyResult<Sdk?> = createAndAddSdk(module, Source.CONFIGURATOR)

  override suspend fun getIntention(module: Module): @IntentionName String? =
    getRequirementsTxtOrSetupPy(module)?.let { PyCharmCommunityCustomizationBundle.message("sdk.create.venv.suggestion", it.name) }

  override suspend fun createAndAddSdkForInspection(module: Module): PyResult<Sdk?> = createAndAddSdk(module, Source.INSPECTION)

  private suspend fun createAndAddSdk(module: Module, source: Source): PyResult<Sdk?> {
    val existingSdks = PythonSdkUtil.getAllSdks()

    val data = askForEnvData(module, existingSdks, source) ?: return PyResult.success(null)

    val (location, chosenBaseSdk, requirementsTxtOrSetupPy) = data
    val systemIndependentLocation = Path(location)
    val projectPath = module.basePath ?: module.project.basePath

    try {
      val sdk = withContext(Dispatchers.EDT) {
        Disposer.newDisposable("Creating virtual environment").use {
          PyTemporarilyIgnoredFileProvider.ignoreRoot(systemIndependentLocation, it)
          createVirtualEnvAndSdkSynchronously(chosenBaseSdk!!, existingSdks, location, projectPath, module.project, module)
        }
      }

      withContext(Dispatchers.EDT) {
        thisLogger().debug("Adding associated virtual environment: ${sdk.homePath}, ${module.basePath}")
        SdkConfigurationUtil.addSdk(sdk)
      }

      val requirementsTxtOrSetupPyFile = VfsUtil.findFile(Paths.get(requirementsTxtOrSetupPy), false)
      if (requirementsTxtOrSetupPyFile == null) {
        PySdkConfigurationCollector.logVirtualEnv(module.project, VirtualEnvResult.DEPS_NOT_FOUND)
        thisLogger().warn("File with dependencies is not found: $requirementsTxtOrSetupPy")
        return PyResult.success(sdk)
      }

      val isRequirements = requirementsTxtOrSetupPyFile.name != SetupPyManager.SETUP_PY

      if (isRequirements) {
        PythonRequirementTxtSdkUtils.saveRequirementsTxtPath(module.project, sdk, requirementsTxtOrSetupPyFile.toNioPath())
      }

      return PythonPackageManager.forSdk(module.project, sdk).sync().mapSuccess { sdk }
    }
    catch (e: ExecutionException) {
      PySdkConfigurationCollector.logVirtualEnv(module.project, VirtualEnvResult.INSTALLATION_FAILURE)
      LOGGER.warn("Exception during creating virtual environment", e)
      return PyResult.localizedError(e.localizedMessage)
    }
  }

  private fun getRequirementsTxtOrSetupPy(module: Module) =
    PyPackageUtil.findRequirementsTxt(module) ?: PyPackageUtil.findSetupPy(module)?.virtualFile

  private suspend fun askForEnvData(module: Module, existingSdks: List<Sdk>, source: Source): PyAddNewVirtualEnvFromFilePanel.Data? {
    val requirementsTxtOrSetupPy = getRequirementsTxtOrSetupPy(module) ?: return null

    var permitted = false
    var envData: PyAddNewVirtualEnvFromFilePanel.Data? = null

    withContext(Dispatchers.EDT) {
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

    override fun postponeValidation(): Boolean = false

    override fun doValidateAll(): List<ValidationInfo> = panel.validateAll(CommonBundle.getOkButtonText())
  }
}
