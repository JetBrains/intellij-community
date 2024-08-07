// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v2

import com.intellij.execution.wsl.WslPath.Companion.parseWindowsUncPath
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.validation.DialogValidationRequestor
import com.intellij.openapi.ui.validation.WHEN_PROPERTY_CHANGED
import com.intellij.openapi.ui.validation.and
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.OSAgnosticPathUtil
import com.intellij.ui.components.ActionLink
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.builder.components.validationTooltip
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.newProject.collector.InterpreterStatisticsInfo
import com.jetbrains.python.newProject.collector.PythonNewProjectWizardCollector
import com.jetbrains.python.sdk.add.v2.PythonInterpreterSelectionMethod.SELECT_EXISTING
import com.jetbrains.python.sdk.add.v2.PythonSupportedEnvironmentManagers.PYTHON
import com.jetbrains.python.statistics.InterpreterCreationMode
import com.jetbrains.python.statistics.InterpreterType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.exists

class PythonNewVirtualenvCreator(model: PythonMutableTargetAddInterpreterModel) : PythonNewEnvironmentCreator(model) {
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

  override fun buildOptions(panel: Panel, validationRequestor: DialogValidationRequestor) {
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
      row(message("sdk.create.custom.base.python")) {
        versionComboBox = pythonInterpreterComboBox(model.state.baseInterpreter,
                                                    model,
                                                    model::addInterpreter,
                                                    model.interpreterLoading)
          .align(Align.FILL)
          .component
      }
      row(message("sdk.create.custom.location")) {
        textFieldWithBrowseButton(message("sdk.create.custom.venv.location.browse.title"),
                                  fileChooserDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor())
          .bindText(model.state.venvPath)
          .whenTextChangedFromUi { locationModified = true }
          .validationRequestor(validationRequestor and WHEN_PROPERTY_CHANGED(model.state.venvPath))
          .cellValidation { textField ->
            addInputRule("") {
              val pathExists = textField.isVisible && textField.doesPathExist()
              locationValidationFailed.set(pathExists)
              if (pathExists) {
                val locationPath = Paths.get(textField.text)
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
                  suggestedVenvName = ".venv"
                  suggestedLocation = locationPath
                  firstFixLink.text = message("sdk.create.custom.venv.use.different.venv.link",
                                              Paths.get("..", locationPath.last().toString(), suggestedVenvName))
                  secondFixLink.isVisible = false
                }
              }
              pathExists
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
        checkBox(message("sdk.create.custom.make.available"))
          .bindSelected(model.state.makeAvailable)
      }
    }


    model.projectPath.afterChange {
      if (!locationModified) {
        val suggestedVirtualEnvPath = model.suggestVenvPath()!! // todo nullability issue
        model.state.venvPath.set(suggestedVirtualEnvPath)
      }
    }

    // todo venv path suggestion from controller
    //model.scope.launch(start = CoroutineStart.UNDISPATCHED) {
    //  presenter.projectWithContextFlow.collectLatest { (projectPath, projectLocationContext) ->
    //    withContext(presenter.uiContext) {
    //      if (!locationModified) {
    //        val suggestedVirtualEnvPath = runCatching {
    //          suggestVirtualEnvPath(projectPath, projectLocationContext)
    //        }.getOrLogException(LOG)
    //        location.set(suggestedVirtualEnvPath.orEmpty())
    //      }
    //    }
    //  }
    //}
  }

  override fun onShown() {
    val modalityState = ModalityState.current().asContextElement()
    model.scope.launch(Dispatchers.EDT + modalityState) {

      val suggestedVirtualEnvPath = model.suggestVenvPath()!! // todo nullability issue
      model.state.venvPath.set(suggestedVirtualEnvPath)

      //val projectBasePath = state.projectPath.get()

      //val basePath = if (model is PythonLocalAddInterpreterModel)
      //  withContext(Dispatchers.IO) {
      //    FileUtil.toSystemDependentName(PySdkSettings.instance.getPreferredVirtualEnvBasePath(projectBasePath))
      //  }
      //else {
      //  ""
      //  // todo fix for wsl and targets
      //  //val suggestedVirtualEnvName = PathUtil.getFileName(projectBasePath)
      //  //val userHome = presenter.projectLocationContext.fetchUserHomeDirectory()
      //  //userHome?.resolve(DEFAULT_VIRTUALENVS_DIR)?.resolve(suggestedVirtualEnvName)?.toString().orEmpty()
      //}
      //
      //model.state.venvPath.set(basePath)
    }

    versionComboBox.setItems(model.baseInterpreters)


  }

  private fun suggestVenvName(currentName: String): String {
    val digitSuffix = currentName.takeLastWhile { it.isDigit() }
    if (digitSuffix == "") return currentName + "1"
    val newSuffix = digitSuffix.toBigInteger().plus(1.toBigInteger()).toString()
    return currentName.removeSuffix(digitSuffix) + newSuffix
  }

  override fun getOrCreateSdk(): Sdk {
    // todo remove project path, or move to controller
    val projectPath = model.projectPath.get()
    assert(projectPath.isNotBlank()) {"Project path can't be blank"}
    return model.setupVirtualenv((Path.of(model.state.venvPath.get())), Path.of(projectPath), model.state.baseInterpreter.get()!!).getOrThrow()
  }

  companion object {


    /**
     * Checks if [this] field's text points to an existing file or directory. Calls [Path.exists] from EDT with care to avoid freezing the
     * UI.
     *
     * In case of Windows machine, this method skips testing UNC paths on existence (except WSL paths) to prevent [Path.exists] checking the
     * network resources. Such checks might freeze the UI for several seconds when performed from EDT. The freezes reveal themselves when
     * a user starts typing a WSL path with `\\w` and continues symbol by symbol (`\\ws`, `\\wsl`, etc.) all the way to the root WSL path.
     *
     * @see [Path.exists]
     */
    @RequiresBackgroundThread(generateAssertion = false)
    private fun TextFieldWithBrowseButton.doesPathExist(): Boolean =
      text.let { probablyIncompletePath ->
        if (SystemInfo.isWindows && OSAgnosticPathUtil.isUncPath(probablyIncompletePath)) {
          val parseWindowsUncPath = parseWindowsUncPath(probablyIncompletePath)
          if (parseWindowsUncPath?.linuxPath?.isNotBlank() == true) {
            probablyIncompletePath.safeCheckCorrespondingPathExist()
          }
          else {
            false
          }
        }
        else {
          probablyIncompletePath.safeCheckCorrespondingPathExist()
        }
      }

    private fun String.safeCheckCorrespondingPathExist() =
      try {
        Paths.get(this).exists()
      }
      catch (e: InvalidPathException) {
        false
      }
  }

  override fun createStatisticsInfo(target: PythonInterpreterCreationTargets): InterpreterStatisticsInfo {
    //val statisticsTarget = if (presenter.projectLocationContext is WslContext) InterpreterTarget.TARGET_WSL else target.toStatisticsField()
    val statisticsTarget = target.toStatisticsField() // todo fix for wsl
    return InterpreterStatisticsInfo(InterpreterType.VIRTUALENV,
                                     statisticsTarget,
                                     model.state.inheritSitePackages.get(),
                                     model.state.makeAvailable.get(),
                                     false,
                                     //presenter.projectLocationContext is WslContext,
                                     false, // todo fix for wsl
                                     InterpreterCreationMode.CUSTOM)
  }
}