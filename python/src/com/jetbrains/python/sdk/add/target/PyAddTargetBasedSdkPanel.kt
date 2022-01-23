// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk.add.target

import com.intellij.CommonBundle
import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.Splitter
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.ui.popup.ListItemDescriptorAdapter
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.JBCardLayout
import com.intellij.ui.components.JBList
import com.intellij.ui.popup.list.GroupedItemsListRenderer
import com.intellij.util.ExceptionUtil
import com.intellij.util.PlatformUtils
import com.intellij.util.ui.JBUI
import com.jetbrains.python.packaging.PyExecutionException
import com.jetbrains.python.sdk.PreferredSdkComparator
import com.jetbrains.python.sdk.PythonSdkType
import com.jetbrains.python.sdk.PythonSdkUtil
import com.jetbrains.python.sdk.add.PyAddSdkPanel
import com.jetbrains.python.sdk.add.PyAddSdkView
import com.jetbrains.python.sdk.add.PyAddSystemWideInterpreterPanel
import com.jetbrains.python.sdk.add.showProcessExecutionErrorDialog
import com.jetbrains.python.sdk.conda.PyCondaSdkCustomizer
import com.jetbrains.python.sdk.pipenv.PyAddPipEnvPanel
import com.jetbrains.python.sdk.poetry.createPoetryPanel
import com.jetbrains.python.target.PythonLanguageRuntimeConfiguration
import java.awt.CardLayout
import java.util.function.Supplier
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * The panel that is supposed to be used both for local and non-local target-based versions of "New Interpreter" dialog.
 */
class PyAddTargetBasedSdkPanel(private val project: Project?,
                               private val module: Module?,
                               private val existingSdks: List<Sdk>,
                               private val targetSupplier: Supplier<TargetEnvironmentConfiguration>?,
                               private val config: PythonLanguageRuntimeConfiguration) {
  private val mainPanel: JPanel = JPanel(JBCardLayout())

  private var selectedPanel: PyAddSdkView? = null
  private val context = UserDataHolderBase()
  private var panels: List<PyAddSdkView> = emptyList()

  private val targetEnvironmentConfiguration: TargetEnvironmentConfiguration?
    get() = targetSupplier?.get()

  private val isUnderLocalTarget: Boolean
    get() = targetEnvironmentConfiguration == null

  fun createCenterPanel(): JComponent {
    val sdks = existingSdks
      .filter { it.sdkType is PythonSdkType && !PythonSdkUtil.isInvalid(it) }
      .sortedWith(PreferredSdkComparator())
    val panels = createPanels(sdks).toMutableList()
    mainPanel.add(SPLITTER_COMPONENT_CARD_PANE, createCardSplitter(panels))
    return mainPanel
  }

  /**
   * Note that creating or using existing Pipenv environments on non-local targets are not yet supported.
   */
  private fun createPanels(sdks: List<Sdk>): List<PyAddSdkView> {
    val venvPanel = PyAddVirtualEnvPanel(project = project,
                                         module = module,
                                         existingSdks = sdks,
                                         allowAddNewVirtualenv = allowCreatingNewEnvironments(project),
                                         context = context,
                                         targetSupplier = targetSupplier,
                                         config = config)
    val condaPanel = if (targetEnvironmentConfiguration.isMutableTarget) createAnacondaPanel() else null
    val systemWidePanel = PyAddSystemWideInterpreterPanel(project, module, existingSdks, context, targetEnvironmentConfiguration, config)
    val newProjectPath = null
    val pipEnvPanel = if (isUnderLocalTarget) createPipEnvPanel(newProjectPath) else null
    val poetryPanel = if (isUnderLocalTarget) createPoetryPanel(project, module, existingSdks, newProjectPath, context) else null
    return if (PyCondaSdkCustomizer.instance.preferCondaEnvironments) {
      listOfNotNull(condaPanel, venvPanel, systemWidePanel, pipEnvPanel, poetryPanel)
    }
    else {
      listOfNotNull(venvPanel, condaPanel, systemWidePanel, pipEnvPanel, poetryPanel)
    }
  }

  fun doValidateAll(): List<ValidationInfo> = selectedPanel?.validateAll() ?: emptyList()

  fun getOrCreateSdk(): Sdk? = selectedPanel?.getOrCreateSdk()

  /**
   * This method is executed after clicking "Finish" button on the last step of the wizard.
   *
   * The provided target [configuration] is expected to be saved within the newly created Python SDK.
   */
  fun getOrCreateSdk(configuration: TargetEnvironmentConfiguration): Sdk? =
    (selectedPanel as? PyAddTargetBasedSdkView)?.getOrCreateSdk(configuration)

  private fun createCardSplitter(panels: List<PyAddSdkView>): Splitter {
    this.panels = panels
    return Splitter(false, 0.25f).apply {
      val cardLayout = CardLayout()
      val cardPanel = JPanel(cardLayout).apply {
        preferredSize = JBUI.size(640, 480)
        for (panel in panels) {
          add(panel.component, panel.panelName)
        }
      }
      val cardsList = JBList(panels).apply {
        val descriptor = object : ListItemDescriptorAdapter<PyAddSdkView>() {
          override fun getTextFor(value: PyAddSdkView) = StringUtil.toTitleCase(value.panelName)
          override fun getIconFor(value: PyAddSdkView) = value.icon
        }
        cellRenderer = object : GroupedItemsListRenderer<PyAddSdkView>(descriptor) {
          override fun createItemComponent() = super.createItemComponent().apply {
            border = JBUI.Borders.empty(4, 4, 4, 10)
          }
        }
        addListSelectionListener {
          selectedPanel = selectedValue
          cardLayout.show(cardPanel, selectedValue.panelName)

          selectedValue.onSelected()
        }
        selectedPanel = panels.getOrNull(0)
        selectedIndex = 0
      }

      firstComponent = cardsList
      secondComponent = cardPanel
    }
  }

  private fun createAnacondaPanel(): PyAddSdkPanel = PyAddCondaEnvPanel(project, module, existingSdks, null, context, targetSupplier,
                                                                        config)

  private fun createPipEnvPanel(newProjectPath: String?) = PyAddPipEnvPanel(project, module, existingSdks, newProjectPath, context)

  /**
   * Tries to create the SDK and closes the dialog if the creation succeeded.
   *
   * @see [doOKAction]
   */
  fun doOKAction() {
    try {
      selectedPanel?.complete()
    }
    catch (e: CreateSdkInterrupted) {
      return
    }
    catch (e: Exception) {
      val cause = ExceptionUtil.findCause(e, PyExecutionException::class.java)
      if (cause == null) {
        Messages.showErrorDialog(e.localizedMessage, CommonBundle.message("title.error"))
      }
      else {
        showProcessExecutionErrorDialog(project, cause)
      }
      return
    }
  }

  companion object {
    private fun allowCreatingNewEnvironments(project: Project?) =
      project != null || !PlatformUtils.isPyCharm() || PlatformUtils.isPyCharmEducational()

    private const val SPLITTER_COMPONENT_CARD_PANE = "Splitter"
  }
}