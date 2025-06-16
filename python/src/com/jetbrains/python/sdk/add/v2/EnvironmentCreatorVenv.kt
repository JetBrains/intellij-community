// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.openapi.application.EDT
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.ui.validation.DialogValidationRequestor
import com.intellij.openapi.ui.validation.WHEN_PROPERTY_CHANGED
import com.intellij.openapi.ui.validation.and
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.ui.components.ActionLink
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.builder.components.validationTooltip
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.errorProcessing.PyResult
import com.jetbrains.python.newProject.collector.InterpreterStatisticsInfo
import com.jetbrains.python.newProjectWizard.collector.PythonNewProjectWizardCollector
import com.jetbrains.python.newProjectWizard.projectPath.ProjectPathFlows.Companion.validatePath
import com.jetbrains.python.sdk.ModuleOrProject
import com.jetbrains.python.sdk.PySdkSettings
import com.jetbrains.python.sdk.add.v2.PythonInterpreterSelectionMethod.SELECT_EXISTING
import com.jetbrains.python.sdk.add.v2.PythonSupportedEnvironmentManagers.PYTHON
import com.jetbrains.python.statistics.InterpreterCreationMode
import com.jetbrains.python.statistics.InterpreterType
import com.jetbrains.python.venvReader.VirtualEnvReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.plus
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists
import kotlin.io.path.isDirectory

class EnvironmentCreatorVenv(model: PythonMutableTargetAddInterpreterModel) : PythonNewEnvironmentCreator(model) {
  private lateinit var versionComboBox: PythonInterpreterComboBox

  private val locationValidationFailed = propertyGraph.property(false)
  private val locationValidationMessage = propertyGraph.property("Current location already exists")
  private var locationModified = false
  private var suggestedVenvName: String = ""
  private var suggestedLocation: Path = Path.of("")

  private val pythonInVenvPath: Path
    get() {
      return when {
        SystemInfo.isWindows -> Paths.get("Scripts", "python.exe")
        //SystemInfo.isWindows && presenter.projectLocationContext !is WslContext -> Paths.get("Scripts", "python.exe")
        else -> Paths.get("bin", "python")
      }
    }

  override fun setupUI(panel: Panel, validationRequestor: DialogValidationRequestor) {
    val firstFixLink = ActionLink(message("sdk.create.custom.venv.use.different.venv.link", ".venv1")) {
      PythonNewProjectWizardCollector.logSuggestedVenvDirFixUsed()
      val newPath = suggestedLocation.resolve(suggestedVenvName)
      model.state.venvPath.set(newPath.toString())
    }
    val secondFixLink = ActionLink(message("sdk.create.custom.venv.select.existing.link")) {
      PythonNewProjectWizardCollector.logExistingVenvFixUsed()
      val sdkPath = Paths.get(model.state.venvPath.get()).resolve(pythonInVenvPath).toString()

      val interpreter = model.findInterpreter(sdkPath) ?: model.addInterpreter(sdkPath)

      model.state.selectedInterpreter.set(interpreter)
      model.navigator.navigateTo(newMethod = SELECT_EXISTING, newManager = PYTHON)
    }

    with(panel) {
      versionComboBox = pythonInterpreterComboBox(
        title = message("sdk.create.custom.base.python"),
        selectedSdkProperty = model.state.baseInterpreter,
        model = model,
        validationRequestor = validationRequestor,
        onPathSelected = model::addInterpreter,
      )
      row(message("sdk.create.custom.location")) {
        // TODO" Extract this logic to the presenter or view model, do not touch nio from EDT, cover with test
        textFieldWithBrowseButton(FileChooserDescriptorFactory.createSingleFolderDescriptor().withTitle(message("sdk.create.custom.venv.location.browse.title")))
          .bindText(model.state.venvPath)
          .whenTextChangedFromUi { locationModified = true }
          .validationRequestor(validationRequestor and WHEN_PROPERTY_CHANGED(model.state.venvPath))
          .cellValidation { textField ->
            addInputRule {
              if (!textField.isVisible) return@addInputRule null // We are hidden, hence valid
              locationValidationFailed.set(false)
              val locationPath = when (val path = validatePath(textField.text)) {
                is com.jetbrains.python.Result.Failure -> return@addInputRule ValidationInfo(path.error.message) // Path is invalid
                is com.jetbrains.python.Result.Success -> path.result
              }
              val pathExists = locationPath.exists()
              locationValidationFailed.set(pathExists)
              if (pathExists) {
                if (locationPath.resolve(pythonInVenvPath).exists()) {
                  val typedName = locationPath.last().toString()
                  suggestedVenvName = suggestVenvName(typedName)
                  suggestedLocation = locationPath.parent ?: Paths.get("/")
                  locationValidationMessage.set(message("sdk.create.custom.venv.environment.exists", typedName))
                  firstFixLink.text = message("sdk.create.custom.venv.use.different.venv.link", suggestedVenvName)
                  secondFixLink.isVisible = true
                }
                else {
                  locationValidationMessage.set(message("sdk.create.custom.venv.folder.not.empty"))
                  suggestedVenvName = VirtualEnvReader.DEFAULT_VIRTUALENV_DIRNAME
                  suggestedLocation = locationPath
                  val suggestedPath = (if (locationPath.isDirectory()) locationPath else locationPath.parent).resolve(suggestedVenvName)
                  firstFixLink.text = message("sdk.create.custom.venv.use.different.venv.link",
                                              suggestedPath)
                  secondFixLink.isVisible = false
                }
              }
              // Path exists means error
              if (pathExists) ValidationInfo(locationValidationMessage.get()) else null
            }
          }
          .align(Align.FILL)
      }

      row("") {
        validationTooltip(locationValidationMessage, firstFixLink, secondFixLink)
          .align(Align.FILL)
      }.visibleIf(locationValidationFailed)

      row("") {
        checkBox(message("sdk.create.custom.inherit.packages"))
          .bindSelected(model.state.inheritSitePackages)
      }
      row("") {
        checkBox(message("available.to.all.projects"))
          .bindSelected(model.state.makeAvailableForAllProjects)
      }
    }
  }

  override fun onShown(scope: CoroutineScope) {
    versionComboBox.initialize(scope, model.baseInterpreters)

    model.projectPathFlows.projectPathWithDefault.onEach {
      if (locationModified) return@onEach

      val preferedFilePath = PySdkSettings.instance.getPreferredVirtualEnvBasePath(it.toString())
      val suggestedVirtualEnvPath = FileUtil.toSystemDependentName(preferedFilePath)
      model.state.venvPath.set(suggestedVirtualEnvPath)
    }.launchIn(scope + Dispatchers.EDT)
  }

  private fun suggestVenvName(currentName: String): String {
    val digitSuffix = currentName.takeLastWhile { it.isDigit() }
    if (digitSuffix == "") return currentName + "1"
    val newSuffix = digitSuffix.toBigInteger().plus(1.toBigInteger()).toString()
    return currentName.removeSuffix(digitSuffix) + newSuffix
  }

  override suspend fun getOrCreateSdk(moduleOrProject: ModuleOrProject): PyResult<Sdk> =
    // todo remove project path, or move to controller
    try {
      val venvPath = Path.of(model.state.venvPath.get())
      model.setupVirtualenv(venvPath, moduleOrProject)
    }
    catch (e: InvalidPathException) {
      PyResult.localizedError(e.localizedMessage)
    }

  override fun createStatisticsInfo(target: PythonInterpreterCreationTargets): InterpreterStatisticsInfo {
    return InterpreterStatisticsInfo(
      type = InterpreterType.VIRTUALENV,
      target = target.toStatisticsField() ,
      globalSitePackage = model.state.inheritSitePackages.get(),
      makeAvailableToAllProjects = model.state.makeAvailableForAllProjects.get(),
      previouslyConfigured = false,
      isWSLContext = false, // todo fix for wsl
      creationMode = InterpreterCreationMode.CUSTOM
    )
  }
}
