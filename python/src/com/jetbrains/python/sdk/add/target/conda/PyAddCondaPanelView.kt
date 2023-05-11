// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.sdk.add.target.conda

import com.intellij.execution.target.TargetBrowserHints
import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.observable.util.bind
import com.intellij.openapi.progress.progressSink
import com.intellij.openapi.progress.runBlockingModalWithRawProgressReporter
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.ui.emptyText
import com.intellij.openapi.util.Disposer
import com.intellij.ui.dsl.builder.*
import com.jetbrains.python.PyBundle
import com.jetbrains.python.run.PythonInterpreterTargetEnvironmentFactory.Companion.projectSyncRows
import com.jetbrains.python.sdk.add.PyAddSdkDialogFlowAction
import com.jetbrains.python.sdk.add.PyAddSdkStateListener
import com.jetbrains.python.sdk.add.target.ProjectSync
import com.jetbrains.python.sdk.add.target.PyAddTargetBasedSdkView
import com.jetbrains.python.sdk.add.target.addBrowseFolderListener
import icons.PythonIcons
import kotlinx.coroutines.Dispatchers
import org.jetbrains.annotations.Nls
import java.awt.Component
import javax.swing.Icon
import javax.swing.JOptionPane
import javax.swing.JOptionPane.ERROR_MESSAGE

/**
 * View for adding conda interpreter backed by [model]
 */
class PyAddCondaPanelView(private val model: PyAddCondaPanelModel) : PyAddTargetBasedSdkView {
  override val panelName: String get() = PyBundle.message("python.add.sdk.panel.name.conda.environment")
  private val disposable = Disposer.newDisposable()

  override val icon: Icon = PythonIcons.Python.Anaconda
  private val condaPathField = TextFieldWithBrowseButton()

  /**
   * Encapsulates the work with the files synchronization options.
   */
  private var projectSync: ProjectSync? = null
  private val panel = panel {

    row(PyBundle.message("python.add.sdk.panel.path.to.conda.field") + ":") {

      cell(condaPathField.apply {
        addBrowseFolderListener(PyBundle.message("python.add.sdk.panel.path.to.conda.field"),
                                model.project,
                                model.targetConfiguration,
                                TargetBrowserHints(false, model.condaPathFileChooser))

      }).applyToComponent { emptyText.text = PyBundle.message("python.add.sdk.panel.path.to.conda.field") }
        .bindText(model.condaPathTextBoxRwProp)
        .columns(COLUMNS_LARGE)
        .trimmedTextValidation(model.condaPathValidator)

      button(PyBundle.message("python.add.sdk.panel.load.envs")) {
        runBlockingModalWithRawProgressReporter(model.project, PyBundle.message("python.sdk.conda.getting.list.envs")) {
          model.onLoadEnvsClicked(Dispatchers.EDT, this.progressSink)
        }.onFailure {
          showError(PyBundle.message("python.sdk.conda.getting.list.envs"), it.localizedMessage)
        }
      }.enabledIf(model.showCondaPathSetOkButtonRoProp)

    }

    buttonsGroup {
      row {
        radioButton(PyBundle.message("python.add.sdk.panel.conda.use.existing"), true).apply {
          this.component.bind(model.condaActionUseExistingEnvRadioRwProp)
        }
        radioButton(PyBundle.message("python.add.sdk.panel.conda.create.new"), false).apply {
          this.component.bind(model.condaActionCreateNewEnvRadioRwProp)
          visibleIf(model.showCondaActionCreateNewEnvRadioRoProp)
        }
      }
    }.bind({ model.condaActionUseExistingEnvRadioRwProp.get() }, {}).visibleIf(model.showCondaActionsPanelRoProp)

    row(PyBundle.message("python.add.sdk.panel.conda.use.existing") + ":") {
      comboBox(model.condaEnvModel)
    }.visibleIf(model.showChooseExistingEnvPanelRoProp)

    row(PyBundle.message("python.add.sdk.panel.conda.env.name")) {
      textField().bindText(model.newEnvNameRwProperty)
    }.visibleIf(model.showCreateNewEnvPanelRoProp)

    row(PyBundle.message("python.add.sdk.python.version")) {
      comboBox(model.languageLevels).bindItem(model.newEnvLanguageLevelRwProperty)
    }.visibleIf(model.showCreateNewEnvPanelRoProp)

    projectSync = projectSyncRows(model.project, model.targetConfiguration)
  }.also { it.registerValidators(disposable) }

  private fun showError(@Nls title: String, @Nls error: String) {
    JOptionPane.showMessageDialog(panel, error, title, ERROR_MESSAGE)
  }

  override val component: Component
    get() = panel

  // Those three functions are from the old (pre-target) interface which is not used anymore
  override fun previous() = Unit
  override fun next() = Unit
  override fun addStateListener(stateListener: PyAddSdkStateListener) = Unit

  override fun onSelected() {
      runBlockingModalWithRawProgressReporter(model.project, PyBundle.message("python.add.sdk.conda.detecting")) {
      model.detectConda(Dispatchers.EDT, progressSink)
    }
  }

  override val actions: Map<PyAddSdkDialogFlowAction, Boolean> = emptyMap()

  override fun complete() {
    Disposer.dispose(disposable)
  }

  override fun validateAll(): List<ValidationInfo> =
    panel.validateAll() + (model.getValidationError()?.let { listOf(ValidationInfo(it)) } ?: emptyList())

  override fun getOrCreateSdk(): Sdk? = getOrCreateSdk(targetEnvironmentConfiguration = null)

  override fun getOrCreateSdk(targetEnvironmentConfiguration: TargetEnvironmentConfiguration?): Sdk? =
    runBlockingModalWithRawProgressReporter(model.project, PyBundle.message("python.add.sdk.panel.wait")) {
      if (targetEnvironmentConfiguration != null) projectSync?.apply(targetEnvironmentConfiguration)
      model.onCondaCreateSdkClicked((Dispatchers.EDT + ModalityState.any().asContextElement()), progressSink,
                                    targetEnvironmentConfiguration).onFailure {
        logger<PyAddCondaPanelModel>().warn(it)
        showError(
          PyBundle.message("python.sdk.conda.cant.create.title"),
          PyBundle.message("python.sdk.conda.cant.create.body", it.localizedMessage))
      }.getOrNull()
    }
}