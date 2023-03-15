// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk.add.target

import com.intellij.CommonBundle
import com.intellij.execution.target.IncompleteTargetEnvironmentConfiguration
import com.intellij.execution.target.TargetEnvironmentConfiguration
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.DialogPanel
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
import com.intellij.util.ui.UIUtil
import com.jetbrains.python.packaging.PyExecutionException
import com.jetbrains.python.sdk.PreferredSdkComparator
import com.jetbrains.python.sdk.PythonSdkType
import com.jetbrains.python.sdk.add.CreateSdkInterrupted
import com.jetbrains.python.sdk.add.PyAddSdkView
import com.jetbrains.python.sdk.add.PyAddSystemWideInterpreterPanel
import com.jetbrains.python.sdk.add.showProcessExecutionErrorDialog
import com.jetbrains.python.sdk.add.target.conda.PyAddCondaPanelModel
import com.jetbrains.python.sdk.add.target.conda.PyAddCondaPanelView
import com.jetbrains.python.sdk.conda.PyCondaSdkCustomizer
import com.jetbrains.python.sdk.pipenv.PyAddPipEnvPanel
import com.jetbrains.python.sdk.poetry.createPoetryPanel
import com.jetbrains.python.sdk.sdkSeemsValid
import com.jetbrains.python.target.PythonLanguageRuntimeConfiguration
import java.awt.CardLayout
import java.awt.Component
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
      .filter { it.sdkType is PythonSdkType && it.sdkSeemsValid }
      .sortedWith(PreferredSdkComparator())
    val (panels, initiallySelectedPanel) = createPanels(sdks)
    mainPanel.add(SPLITTER_COMPONENT_CARD_PANE, createCardSplitter(panels, initiallySelectedPanel))
    return mainPanel
  }

  /**
   * Note that creating or using existing Pipenv environments on non-local targets are not yet supported.
   *
   * @return the pair containing the list of panels and the initially selected panel
   */
  private fun createPanels(sdks: List<Sdk>): Pair<List<PyAddSdkView>, PyAddSdkView> {
    val venvPanel = PyAddVirtualEnvPanel(project = project,
                                         module = module,
                                         existingSdks = sdks,
                                         allowAddNewVirtualenv = allowCreatingNewEnvironments(project),
                                         context = context,
                                         targetSupplier = targetSupplier,
                                         config = config)
    val systemWidePanel = PyAddSystemWideInterpreterPanel(project, module, existingSdks, context, targetSupplier, config)
    val condaPanel = createAnacondaPanel()
    return when {
      isUnderLocalTarget -> {
        val newProjectPath = null
        val pipEnvPanel = createPipEnvPanel(newProjectPath)
        val poetryPanel = createPoetryPanel(project, module, existingSdks, newProjectPath, context)
        if (PyCondaSdkCustomizer.instance.preferCondaEnvironments) {
          listOf(condaPanel, venvPanel, systemWidePanel, pipEnvPanel, poetryPanel) to condaPanel
        }
        else {
          listOf(venvPanel, condaPanel, systemWidePanel, pipEnvPanel, poetryPanel) to venvPanel
        }
      }
      targetEnvironmentConfiguration.isMutableTarget -> mutableListOf<PyAddSdkView>(venvPanel, systemWidePanel).apply {
        // Conda not supported for SSH (which is mutable incomplete environment)
        if (targetEnvironmentConfiguration !is IncompleteTargetEnvironmentConfiguration) add(condaPanel)
      } to venvPanel
      else -> listOf(venvPanel, systemWidePanel, condaPanel) to systemWidePanel
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

  private fun createCardSplitter(panels: List<PyAddSdkView>, initiallySelectedPanel: PyAddSdkView): Splitter {
    this.panels = panels
    return Splitter(false, 0.25f).apply {
      val cardLayout = CardLayout()
      val cardPanel = JPanel(cardLayout).apply {
        preferredSize = JBUI.size(640, 480)
        for (panel in panels) {
          add(panel.component.applyDefaultInsets(), panel.panelName)
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
          // Only last even must be processed. Other events may leave UI in inconsistent state
          if (it.valueIsAdjusting) return@addListSelectionListener
          selectedPanel = selectedValue
          cardLayout.show(cardPanel, selectedValue.panelName)

          selectedValue.onSelected()
        }
        selectedPanel = initiallySelectedPanel
        selectedIndex = panels.indexOf(initiallySelectedPanel)
      }

      firstComponent = cardsList
      secondComponent = cardPanel
    }
  }

  private fun createAnacondaPanel(): PyAddSdkView = PyAddCondaPanelView(
    PyAddCondaPanelModel(targetEnvironmentConfiguration, existingSdks, project!!))

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

    /**
     * Applies the empty border (i.e. adds insets) to the nearest [DialogPanel].
     *
     * [com.intellij.ui.dsl.gridLayout.impl.GridImpl] adds extra gaps "to guarantee no visual clippings (like focus rings)" are present.
     * This results in an additional offset if checkboxes or radio buttons are added to the panel. We compensate this by adding the proper
     * border directly to the [DialogPanel].
     *
     * @see com.intellij.ui.dsl.gridLayout.impl.LayoutData.outsideGaps
     * @see com.intellij.ui.dsl.gridLayout.impl.GridImpl.getPreferredSizeData
     */
    private fun <T : Component> T.applyDefaultInsets(): T = apply {
      if (this is JComponent) {
        val dialogPanel = UIUtil.findComponentOfType(this, DialogPanel::class.java)
        (dialogPanel ?: this).border = JBUI.Borders.empty(4, 9, 4, 15)
      }
    }
  }
}