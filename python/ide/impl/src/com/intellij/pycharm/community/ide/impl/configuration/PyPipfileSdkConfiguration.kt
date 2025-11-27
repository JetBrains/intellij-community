// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.configuration

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.projectRoots.impl.SdkConfigurationUtil
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.pycharm.community.ide.impl.PyCharmCommunityCustomizationBundle
import com.intellij.pycharm.community.ide.impl.configuration.PySdkConfigurationCollector.InputData
import com.intellij.pycharm.community.ide.impl.configuration.PySdkConfigurationCollector.PipEnvResult
import com.intellij.pycharm.community.ide.impl.configuration.PySdkConfigurationCollector.Source
import com.intellij.pycharm.community.ide.impl.findEnvOrNull
import com.intellij.python.common.tools.ToolId
import com.intellij.python.community.execService.ZeroCodeStdoutParserTransformer
import com.intellij.python.community.impl.pipenv.pipenvPath
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.jetbrains.python.PyBundle
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.orLogException
import com.jetbrains.python.sdk.PythonSdkType
import com.jetbrains.python.sdk.basePath
import com.jetbrains.python.sdk.configuration.*
import com.jetbrains.python.sdk.findAmongRoots
import com.jetbrains.python.sdk.impl.PySdkBundle
import com.jetbrains.python.sdk.impl.resolvePythonBinary
import com.jetbrains.python.sdk.legacy.PythonSdkUtil
import com.jetbrains.python.sdk.pipenv.*
import com.jetbrains.python.sdk.pipenv.ui.PyAddNewPipEnvFromFilePanel
import com.jetbrains.python.sdk.setAssociationToModule
import com.jetbrains.python.venvReader.VirtualEnvReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.awt.BorderLayout
import java.nio.file.Path
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.io.path.isExecutable
import kotlin.io.path.pathString

private val LOGGER = Logger.getInstance(PyPipfileSdkConfiguration::class.java)

@ApiStatus.Internal
class PyPipfileSdkConfiguration : PyProjectSdkConfigurationExtension {

  override val toolId: ToolId = PIPENV_TOOL_ID

  override suspend fun checkEnvironmentAndPrepareSdkCreator(module: Module): CreateSdkInfo? = prepareSdkCreator(
    { checkManageableEnv(module, it) }
  ) { envExists ->
    { needsConfirmation -> createAndAddSdk(module, if (needsConfirmation) Source.CONFIGURATOR else Source.INSPECTION, envExists) }
  }

  override fun asPyProjectTomlSdkConfigurationExtension(): PyProjectTomlConfigurationExtension? = null

  private suspend fun checkManageableEnv(
    module: Module, checkExistence: CheckExistence,
  ): EnvCheckerResult = withBackgroundProgress(module.project, PyBundle.message("python.sdk.validating.environment")) {
    val pipfile = findAmongRoots(module, PipEnvFileHelper.PIP_FILE)?.name ?: return@withBackgroundProgress EnvCheckerResult.CannotConfigure
    val pipEnvExecutable = getPipEnvExecutable() ?: return@withBackgroundProgress EnvCheckerResult.CannotConfigure
    val canManage = pipEnvExecutable.isExecutable()
    val intentionName = PyCharmCommunityCustomizationBundle.message("sdk.create.pipenv.suggestion", pipfile)
    val envNotFound = EnvCheckerResult.EnvNotFound(intentionName)

    when {
      canManage && checkExistence -> {
        PropertiesComponent.getInstance().pipenvPath = pipEnvExecutable.pathString
        val envPath = runPipEnv(
          module.basePath?.toNioPathOrNull(),
          "--venv",
          transformer = ZeroCodeStdoutParserTransformer { PyResult.success(Path.of(it)) }
        ).successOrNull
        val path = envPath?.resolvePythonBinary()
        val envExists = path?.let {
          LocalFileSystem.getInstance().refreshAndFindFileByPath(it.pathString) != null
        } ?: false
        if (envExists) {
          path.findEnvOrNull(intentionName) ?: envNotFound
        }
        else envNotFound
      }
      canManage -> envNotFound
      else -> EnvCheckerResult.CannotConfigure
    }
  }

  private suspend fun createAndAddSdk(module: Module, source: Source, envExists: Boolean): PyResult<Sdk?> {
    val pipEnvExecutable = askForEnvData(module, source, envExists) ?: return PyResult.success(null)
    PropertiesComponent.getInstance().pipenvPath = pipEnvExecutable.pipEnvPath.pathString
    return createOrUsePipEnv(module)
  }

  private suspend fun askForEnvData(module: Module, source: Source, envExists: Boolean): PyAddNewPipEnvFromFilePanel.Data? {
    val pipEnvExecutable = getPipEnvExecutable()

    if ((envExists || source == Source.INSPECTION) && pipEnvExecutable?.isExecutable() == true) {
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

  private suspend fun createOrUsePipEnv(module: Module): PyResult<Sdk> {
    LOGGER.debug("Creating pipenv environment")
    return withBackgroundProgress(module.project, PyBundle.message("python.sdk.using.pipenv.sentence")) {
      val basePath = module.basePath
                     ?: return@withBackgroundProgress PyResult.localizedError(PyBundle.message("python.sdk.provided.path.is.invalid", module.basePath))
      val pipEnv = setupPipEnv(Path.of(basePath), null, true).getOr {
        PySdkConfigurationCollector.logPipEnv(module.project, PipEnvResult.CREATION_FAILURE)
        return@withBackgroundProgress it
      }

      val path = withContext(Dispatchers.IO) { VirtualEnvReader.Instance.findPythonInPythonRoot(Path.of(pipEnv)) }
      if (path == null) {
        return@withBackgroundProgress PyResult.localizedError(PySdkBundle.message("cannot.find.executable", "python", pipEnv))
      }

      val file = LocalFileSystem.getInstance().refreshAndFindFileByPath(path.toString())
      if (file == null) {
        return@withBackgroundProgress PyResult.localizedError(PySdkBundle.message("cannot.find.executable", "python", path))
      }

      PySdkConfigurationCollector.logPipEnv(module.project, PipEnvResult.CREATED)
      LOGGER.debug("Setting up associated pipenv environment: $path, $basePath")

      val sdk = SdkConfigurationUtil.setupSdk(
        PythonSdkUtil.getAllSdks().toTypedArray(),
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

      PyResult.success(sdk)
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
        val border = IdeBorderFactory.createEmptyBorder(JBUI.insets(4, 0, 6, 0))
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
