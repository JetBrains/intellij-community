// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.target.conda

import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.observable.properties.ObservableProperty
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.progress.ProgressSink
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.execution.target.FullPathOnTarget
import com.jetbrains.python.PyBundle
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.sdk.add.target.isMutableTarget
import com.jetbrains.python.sdk.flavors.conda.NewCondaEnvRequest
import com.jetbrains.python.sdk.flavors.conda.PyCondaCommand
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnv
import com.jetbrains.python.sdk.flavors.conda.PyCondaEnvIdentity
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.Nls
import javax.swing.DefaultComboBoxModel
import kotlin.coroutines.CoroutineContext

/**
 * Model for [PyAddCondaPanelView]
 * Each boundable property ends with "Prop" and either "ro" (view can read it) or "rw" (view can set it)
 *
 * First, user fills [condaPathTextBoxRwProp] then clicks [onLoadEnvsClicked]
 * (if [showCondaPathSetOkButtonRoProp] is true).
 *
 * After path validation [showCondaActionsPanelRoProp] becomes "true", hence radio buttons should be displayed
 * one for "use existing" and one for new if [showCondaActionCreateNewEnvRadioRoProp].
 *
 * See [com.jetbrains.env.conda.PyAddCondaPanelModelTest]
 */
class PyAddCondaPanelModel(val targetConfiguration: TargetEnvironmentConfiguration?,
                           private val existingSdks: List<Sdk>,
                           val project: Project) {
  private val propertyGraph = PropertyGraph()

  /**
   * Python versions for new environment
   */
  val languageLevels: List<LanguageLevel> = condaSupportedLanguages

  val condaPathFileChooser: FileChooserDescriptor = FileChooserDescriptor(true, false, false, false, false, false)

  /**
   * If target is mutable we can create new env on it
   */
  private val allowCreateNewEnv: Boolean = targetConfiguration?.isMutableTarget ?: true

  /**
   * Path to conda binary
   */
  val condaPathTextBoxRwProp: ObservableMutableProperty<FullPathOnTarget> = propertyGraph.property("")

  /**
   * "Ok" button after conda binary path should be shown
   */
  val showCondaPathSetOkButtonRoProp: ObservableProperty<Boolean> = propertyGraph.property(false).apply {
    dependsOn(condaPathTextBoxRwProp) { condaPathTextBoxRwProp.get().trim().isNotEmpty() }
  }

  /**
   * "create" or "use existing" radio buttons have to be shown
   */
  val showCondaActionsPanelRoProp = propertyGraph.property(false)

  /**
   * Model for the list of existing conda envs
   */
  val condaEnvModel: DefaultComboBoxModel<PyCondaEnvIdentity> = DefaultComboBoxModel()

  /**
   * "Create new conda" radio button
   */
  val condaActionCreateNewEnvRadioRwProp: ObservableMutableProperty<Boolean> = propertyGraph.property(false)

  /**
   * "Use existing conda" radio button
   */
  val condaActionUseExistingEnvRadioRwProp = propertyGraph.property(true)

  /**
   * "Choose existing conda env" panel should be displayed
   */
  val showChooseExistingEnvPanelRoProp: ObservableProperty<Boolean> = propertyGraph.property(false).apply {
    trueIfAll(showCondaActionsPanelRoProp, condaActionUseExistingEnvRadioRwProp)
  }

  /**
   * "Create new conda env" panel should be displayed
   */
  val showCreateNewEnvPanelRoProp: ObservableProperty<Boolean> = propertyGraph.property(false).apply {
    if (allowCreateNewEnv) {
      trueIfAll(showCondaActionsPanelRoProp, condaActionCreateNewEnvRadioRwProp)
    }
  }

  /**
   * Name for the new conda environment
   */
  val newEnvNameRwProperty: ObservableMutableProperty<String> = propertyGraph.property(if (project.isDefault) "" else project.name)

  /**
   * Python version for the new conda environment dropdown
   */
  val newEnvLanguageLevelRwProperty: ObservableMutableProperty<LanguageLevel> = propertyGraph.property(LanguageLevel.PYTHON38)

  /**
   * "Conda create new env" radio button should be shown
   */
  val showCondaActionCreateNewEnvRadioRoProp: ObservableProperty<Boolean> = propertyGraph.property(false).apply {
    if (allowCreateNewEnv) {
      trueIfAll(showCondaActionsPanelRoProp)
    }
  }

  /**
   * Loaded list of existing conda envs
   */
  private var condaEnvs: Result<CondaInfo> = Result.failure(Exception(PyBundle.message("python.sdk.conda.no.exec")))

  /**
   * To be called when user sets path to conda and clicks "Load Envs".
   *
   * [uiContext] is for swing.
   * Result may contain an error
   */
  suspend fun onLoadEnvsClicked(uiContext: CoroutineContext,
                                progressSink: ProgressSink? = null): Result<List<PyCondaEnv>> = withContext(uiContext) {
    val path = condaPathTextBoxRwProp.get()
    progressSink?.text(PyBundle.message("python.sdk.conda.getting.list.envs"))
    PyCondaEnv.getEnvs(PyCondaCommand(path.trim(), targetConfiguration))
      .onFailure {
        condaEnvs = Result.failure(it)
        condaEnvModel.removeAllElements()
        showCondaActionsPanelRoProp.set(false)
      }
      .onSuccess { condaEnvsList ->
        condaEnvModel.removeAllElements()
        condaEnvs = Result.success(CondaInfo(path, condaEnvsList))
        condaEnvModel.addAll(condaEnvsList.map { it.envIdentity })
        condaEnvModel.selectedItem = condaEnvModel.getElementAt(0)
        showCondaActionsPanelRoProp.set(true)
      }
  }


  private val notEmptyRegex = Regex("^[a-zA-Z0-9_]+$")


  /**
   * Detects condas in well-known locations so user doesn't have to provide conda path
   */
  suspend fun detectConda(uiContext: CoroutineContext, progressSink: ProgressSink? = null) {
    if (withContext(uiContext) {
        // Already set, no need to detect
        condaPathTextBoxRwProp.get().isNotBlank()
      }) return
    val condaPath = suggestCondaPath(targetConfiguration) ?: return
    withContext(uiContext) {
      condaPathTextBoxRwProp.set(condaPath)
      // Since path is set, lets click button on behalf of user
      onLoadEnvsClicked(uiContext, progressSink)
    }
  }

  /**
   * @return either null (if no error) or localized error string
   */
  fun getValidationError(): @Nls String? {

    val envIdentities = condaEnvs.getOrElse {
      return it.message ?: PyBundle.message("python.sdk.conda.problem.running")
    }.envs.map { it.envIdentity }.filterIsInstance<PyCondaEnvIdentity.NamedEnv>().map { it.envName }


    if (showCreateNewEnvPanelRoProp.get()) {
      // Create new env
      val newEnvName = newEnvNameRwProperty.get()
      if (!newEnvName.matches(notEmptyRegex)) {
        return PyBundle.message("python.sdk.conda.problem.env.empty.invalid")
      }
      else if (newEnvName in envIdentities) {
        return PyBundle.message("python.sdk.conda.problem.env.name.used")
      }
    }
    return null
  }

  /**
   * User clicked on "OK" after choosing either create new or use existing env.
   * The process of creation reported to [progressSink]. Result is SDK or error.
   */
  suspend fun onCondaCreateSdkClicked(uiContext: CoroutineContext,
                                      progressSink: ProgressSink?): Result<Sdk> {

    val pyCondaCommand = PyCondaCommand(condaPathTextBoxRwProp.get(), targetConfiguration, project)
    if (condaActionUseExistingEnvRadioRwProp.get()) {
      // Use existing env
      return Result.success(
        pyCondaCommand.createCondaSdkFromExistingEnv(condaEnvModel.selectedItem as PyCondaEnvIdentity, existingSdks, project))

    }
    else {
      // Create new
      return pyCondaCommand.createCondaSdkAlongWithNewEnv(
        NewCondaEnvRequest.EmptyNamedEnv(newEnvLanguageLevelRwProperty.get(), newEnvNameRwProperty.get()),
        uiContext,
        existingSdks,
        project,
        progressSink)
    }
  }

  private data class CondaInfo(val pathToConda: FullPathOnTarget, val envs: List<PyCondaEnv>)

  /**
   * True to all [props] are also true
   */
  private fun GraphProperty<Boolean>.trueIfAll(vararg props: ObservableMutableProperty<Boolean>) {
    for (prop in props) {
      dependsOn(prop) {
        props.all { it.get() }
      }
    }
  }
}
