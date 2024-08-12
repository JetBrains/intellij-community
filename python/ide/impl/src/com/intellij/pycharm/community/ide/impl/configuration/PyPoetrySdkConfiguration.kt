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
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.StandardFileSystems
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.pycharm.community.ide.impl.PyCharmCommunityCustomizationBundle
import com.jetbrains.python.sdk.*
import com.jetbrains.python.sdk.configuration.PyProjectSdkConfigurationExtension
import com.jetbrains.python.sdk.poetry.*
import com.jetbrains.python.sdk.poetry.ui.PyAddNewPoetryFromFilePanel
import java.awt.BorderLayout
import java.nio.file.Path
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.io.path.pathString

/**
 *  This source code is created by @koxudaxi Koudai Aono <koxudaxi@gmail.com>
 */

class PyPoetrySdkConfiguration : PyProjectSdkConfigurationExtension {
  companion object {
    private val LOGGER = Logger.getInstance(PyPoetrySdkConfiguration::class.java)
  }

  override fun createAndAddSdkForConfigurator(module: Module): Sdk? = createAndAddSDk(module, false)

  override fun getIntention(module: Module): @IntentionName String? =
    module.pyProjectToml?.let { PyCharmCommunityCustomizationBundle.message("sdk.set.up.poetry.environment", it.name) }

  override fun createAndAddSdkForInspection(module: Module): Sdk? = createAndAddSDk(module, true)

  private fun createAndAddSDk(module: Module, inspection: Boolean): Sdk? {
    val poetryEnvExecutable = askForEnvData(module, inspection) ?: return null
    PropertiesComponent.getInstance().poetryPath = poetryEnvExecutable.poetryPath.pathString
    return createPoetry(module)
  }

  private fun askForEnvData(module: Module, inspection: Boolean): PyAddNewPoetryFromFilePanel.Data? {
    val poetryExecutable = getPoetryExecutable()

    if (inspection && validatePoetryExecutable(poetryExecutable) == null) {
      return PyAddNewPoetryFromFilePanel.Data(poetryExecutable!!)
    }

    var permitted = false
    var envData: PyAddNewPoetryFromFilePanel.Data? = null

    ApplicationManager.getApplication().invokeAndWait {
      val dialog = Dialog(module)

      permitted = dialog.showAndGet()
      envData = dialog.envData

      LOGGER.debug("Dialog exit code: ${dialog.exitCode}, $permitted")
    }

    return if (permitted) envData else null
  }

  private fun createPoetry(module: Module): Sdk? {
    ProgressManager.progress(PyCharmCommunityCustomizationBundle.message("sdk.progress.text.setting.up.poetry.environment"))
    LOGGER.debug("Creating poetry environment")

    val basePath = module.basePath?.let { Path.of(it) } ?: return null
    val poetry = try {
      val init = StandardFileSystems.local().findFileByPath(basePath.pathString)?.findChild(PY_PROJECT_TOML)?.let {
        getPyProjectTomlForPoetry(it)
      } == null
      setupPoetry(basePath, null, true, init)
    }
    catch (e: ExecutionException) {
      LOGGER.warn("Exception during creating poetry environment", e)
      showSdkExecutionException(null, e,
                                PyCharmCommunityCustomizationBundle.message("sdk.dialog.title.failed.to.set.up.poetry.environment"))
      return null
    }

    val path = VirtualEnvReader.Instance.findPythonInPythonRoot(Path.of(poetry)).also {
      if (it == null) {
        LOGGER.warn("Python executable is not found: $poetry")
      }
    } ?: return null

    val file = LocalFileSystem.getInstance().refreshAndFindFileByPath(path.toString()).also {
      if (it == null) {
        LOGGER.warn("Python executable file is not found: $path")
      }
    } ?: return null

    LOGGER.debug("Setting up associated poetry environment: $path, $basePath")
    val sdk = SdkConfigurationUtil.setupSdk(
      ProjectJdkTable.getInstance().allJdks,
      file,
      PythonSdkType.getInstance(),
      null,
      suggestedSdkName(basePath)
    )

    ApplicationManager.getApplication().invokeAndWait {
      LOGGER.debug("Adding associated poetry environment: $path, $basePath")
      sdk.isPoetry = true
      sdk.setAssociationToModule(module)
      SdkConfigurationUtil.addSdk(sdk)
    }

    return sdk
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