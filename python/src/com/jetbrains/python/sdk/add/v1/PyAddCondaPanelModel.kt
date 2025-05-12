// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.v1

import com.intellij.execution.target.FullPathOnTarget
import com.intellij.execution.target.LanguageRuntimeType
import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.execution.target.local.LocalTargetEnvironmentRequest
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.observable.properties.ObservableProperty
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.validation.validationErrorIf
import com.intellij.platform.util.progress.RawProgressReporter
import com.jetbrains.python.PyBundle
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.sdk.conda.*
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
 * First, user fills [condaPathTextBoxRwProp] (must be validated with [condaPathValidator]) then clicks [onLoadEnvsClicked]
 * (if [showCondaPathSetOkButtonRoProp] is true).
 *
 * After path validation [showCondaActionsPanelRoProp] becomes "true", hence radio buttons should be displayed
 * one for "use existing" and one for new if [showCondaActionCreateNewEnvRadioRoProp].
 *
 * See [com.jetbrains.env.conda.PyAddCondaPanelModelTest]
 */
class PyAddCondaPanelModel(val targetConfiguration: TargetEnvironmentConfiguration?,
                           private val existingSdks: List<Sdk>,
                           val project: Project,
                           val introspectable: LanguageRuntimeType.Introspectable? = null) {
  private val propertyGraph = PropertyGraph()

  /**
   * Python versions for new environment
   */
  val languageLevels: List<LanguageLevel> = condaSupportedLanguages

  val condaPathFileChooser: FileChooserDescriptor
    get() = FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor().withFileFilter { condaPathIsValid(it.path) }

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
    dependsOn(condaPathTextBoxRwProp) { condaPathIsValid(condaPathTextBoxRwProp.get()) }
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

  private val targetCommandExecutor: TargetCommandExecutor =
    if (introspectable != null) IntrospectableCommandExecutor(introspectable)
    else TargetEnvironmentRequestCommandExecutor(targetConfiguration?.createEnvironmentRequest(project) ?: LocalTargetEnvironmentRequest())

  /**
   * To be called when user sets path to conda and clicks "Load Envs".
   *
   * [uiContext] is for swing.
   * Result may contain an error
   */
  suspend fun onLoadEnvsClicked(uiContext: CoroutineContext,
                                reporter: RawProgressReporter? = null): Result<List<PyCondaEnv>> = withContext(uiContext) {
    val path = condaPathTextBoxRwProp.get()
    reporter?.text(PyBundle.message("python.sdk.conda.getting.list.envs"))
    PyCondaEnv.getEnvs(targetCommandExecutor, path.trim())
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


  private val notEmptyRegex = Regex("^[a-zA-Z0-9_-]+$")
  private val condaPathRegex = Regex(".*[/\\\\]conda(\\.\\w{1,3})?$")

  /**
   * View validator for conda path
   */
  val condaPathValidator = validationErrorIf<String>(PyBundle.message("python.sdk.select.conda.path.title")) {
    !condaPathIsValid(it)
  }

  private fun condaPathIsValid(path: FullPathOnTarget): Boolean = path.matches(condaPathRegex)


  /**
   * Detects condas in well-known locations so user doesn't have to provide conda path
   */
  suspend fun detectConda(uiContext: CoroutineContext, reporter: RawProgressReporter? = null) {
    if (withContext(uiContext) {
        // Already set, no need to detect
        condaPathTextBoxRwProp.get().isNotBlank()
      }) return
    val condaPath = suggestCondaPath(targetCommandExecutor)
    if (condaPath == null) {
      withContext(uiContext) {
        condaPathTextBoxRwProp.set("")
      }
      return
    }
    withContext(uiContext) {
      condaPathTextBoxRwProp.set(condaPath)
      // Since path is set, lets click button on behalf of user
      onLoadEnvsClicked(uiContext, reporter)
    }
  }

  /**
   * @return either null (if no error) or localized error string
   */
  fun getValidationError(): @Nls String? {
    val envIdentities = getEnvIdentities().getOrElse { return it.message ?: PyBundle.message("python.sdk.conda.problem.running") }

    return if (showCreateNewEnvPanelRoProp.get()) {
      validateEnvIdentitiesName(envIdentities)
    }
    else {
      null
    }
  }

  /**
   * This method returns envIdentities from loaded envs
   */
  private fun getEnvIdentities(): Result<List<PyCondaEnvIdentity.NamedEnv>> =
    condaEnvs.map { loadedEnvs ->
      loadedEnvs.envs.map { it.envIdentity }.filterIsInstance<PyCondaEnvIdentity.NamedEnv>()
    }

  /**
   * @return either null (if no error in name validation) or localized error string
   */
  private fun validateEnvIdentitiesName(envIdentities: List<PyCondaEnvIdentity.NamedEnv>): @Nls String? {
    // Create new env
    val newEnvName = newEnvNameRwProperty.get()
    if (!newEnvName.matches(notEmptyRegex)) {
      return PyBundle.message("python.sdk.conda.problem.env.empty.invalid")
    }
    else if (newEnvName in envIdentities.map { it.envName }) {
      return PyBundle.message("python.sdk.conda.problem.env.name.used")
    }
    return null
  }

  /**
   * User clicked on "OK" after choosing either create new or use existing env.
   * The process of creation reported to [reporter]. Result is SDK or error.
   *
   * @param targetConfiguration the target configuration with the corresponding data saved, it must *not* implement
   * [com.intellij.execution.target.IncompleteTargetEnvironmentConfiguration]
   */
  suspend fun onCondaCreateSdkClicked(uiContext: CoroutineContext,
                                      reporter: RawProgressReporter?,
                                      targetConfiguration: TargetEnvironmentConfiguration?): Result<Sdk> {

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
        reporter)
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
