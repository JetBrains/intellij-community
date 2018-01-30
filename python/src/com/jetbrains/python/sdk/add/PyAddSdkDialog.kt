/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.sdk.add

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Splitter
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.ui.popup.ListItemDescriptorAdapter
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.JBCardLayout
import com.intellij.ui.components.JBList
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.ui.popup.list.GroupedItemsListRenderer
import com.intellij.util.PlatformUtils
import com.intellij.util.ui.GridBag
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.jetbrains.python.sdk.PreferredSdkComparator
import com.jetbrains.python.sdk.PythonSdkType
import com.jetbrains.python.sdk.add.PyAddSdkDialog.Companion.create
import com.jetbrains.python.sdk.add.wizard.WizardStep
import com.jetbrains.python.sdk.detectVirtualEnvs
import com.jetbrains.python.sdk.isAssociatedWithProject
import icons.PythonIcons
import java.awt.*
import java.awt.event.ActionEvent
import javax.swing.*

/**
 * The dialog may look like the normal dialog with OK, Cancel and Help buttons
 * or the wizard dialog with Next, Previous, Finish, Cancel and Help buttons.
 *
 * Use [create] to instantiate the dialog.
 *
 * @author vlan
 */
class PyAddSdkDialog private constructor(private val project: Project?,
                                         private val existingSdks: List<Sdk>,
                                         private val newProjectPath: String?) : DialogWrapper(project) {
  /**
   * This is the main panel that supplies sliding effect for the wizard states.
   */
  private val mainPanel: JPanel = JPanel(JBCardLayout())

  private var selectedPanel: PyAddSdkView? = null
  private var panels: List<PyAddSdkView> = emptyList()

  init {
    title = "Add Local Python Interpreter"
  }

  override fun createCenterPanel(): JComponent {
    val sdks = existingSdks
      .filter { it.sdkType is PythonSdkType && !PythonSdkType.isInvalid(it) }
      .sortedWith(PreferredSdkComparator())
    val panels = arrayListOf<PyAddSdkView>(createVirtualEnvPanel(project, sdks, newProjectPath),
                                           createAnacondaPanel(project),
                                           PyAddSystemWideInterpreterPanel(existingSdks))
    val extendedPanels = PyAddSdkProvider.EP_NAME.extensions.map { it.createView() }
    panels.addAll(extendedPanels)
    mainPanel.add(SPLITTER_COMPONENT, createCardSplitter(panels))
    return mainPanel
  }

  private var navigationPanelCardLayout: CardLayout? = null

  private var southPanel: JPanel? = null

  private var selectedWizardStep: WizardStep<Sdk?>? = null

  override fun createSouthPanel(): JComponent {
    val regularDialogSouthPanel = super.createSouthPanel()
    val wizardDialogSouthPanel = createWizardSouthPanel()

    navigationPanelCardLayout = CardLayout()

    val result = JPanel(navigationPanelCardLayout).apply {
      add(regularDialogSouthPanel, REGULAR_CARD_PANE)
      add(wizardDialogSouthPanel, WIZARD_CARD_PANE)
    }

    southPanel = result

    return result
  }

  private fun createWizardSouthPanel(): JPanel {
    assert(value = style != DialogStyle.COMPACT,
           lazyMessage = { "${PyAddSdkDialog::class.java} is not ready for ${DialogStyle.COMPACT} dialog style" })

    val panel = JPanel(BorderLayout())
    //noinspection UseDPIAwareInsets
    val insets = if (SystemInfo.isMacOSLeopard)
      if (UIUtil.isUnderIntelliJLaF()) JBUI.insets(0, 8) else JBUI.emptyInsets()
    else if (UIUtil.isUnderWin10LookAndFeel()) JBUI.emptyInsets() else Insets(8, 0, 0, 0) //don't wrap to JBInsets

    val bag = GridBag().setDefaultInsets(insets)

    val lrButtonsPanel = NonOpaquePanel(GridBagLayout())

    val rightButtonsPanel = createButtonsPanel(listOf(previousButton.value))
    rightButtonsPanel.border = BorderFactory.createEmptyBorder(0, 0, 0, 20)  // leave some space between button groups
    lrButtonsPanel.add(rightButtonsPanel, bag.next())

    lrButtonsPanel.add(Box.createHorizontalGlue(), bag.next().weightx(1.0).fillCellHorizontally())   // left strut

    val buttonsPanel = createButtonsPanel(listOf(nextButton.value, cancelButton.value))

    lrButtonsPanel.add(buttonsPanel, bag.next())

    panel.add(lrButtonsPanel, BorderLayout.CENTER)

    panel.border = JBUI.Borders.emptyTop(8)

    return panel
  }

  private val nextAction: Action = object : DialogWrapperAction("Next") {
    override fun doAction(e: ActionEvent) = onNext()
  }

  private val nextButton = lazy { createJButtonForAction(nextAction) }

  private val previousAction = object : DialogWrapperAction("Previous") {
    override fun doAction(e: ActionEvent) = onPrevious()
  }

  private val previousButton = lazy { createJButtonForAction(previousAction) }

/*
  private fun createAnotherCancelAction(): Action = object : DialogWrapperAction("Cancel") {
    override fun doAction(e: ActionEvent) = doCancelAction(e)
  }
*/

  private val cancelButton = lazy { createJButtonForAction(cancelAction) }

  private fun createButtonsPanel(buttons: List<JButton>): JPanel {
    val hgap = if (SystemInfo.isMacOSLeopard) if (UIUtil.isUnderIntelliJLaF()) 8 else 0 else 5
    val buttonsPanel = NonOpaquePanel(GridLayout(1, buttons.size, hgap, 0))
    for (button in buttons) {
      buttonsPanel.add(button)
    }
    return buttonsPanel
  }

  override fun postponeValidation() = false

  override fun doValidateAll(): List<ValidationInfo> = selectedWizardStep?.validateAll() ?: emptyList()

  fun getOrCreateSdk(): Sdk? = selectedWizardStep?.finish()

  private fun createCardSplitter(panels: List<PyAddSdkView>): Splitter {
    this.panels = panels
    return Splitter(false, 0.25f).apply {
      val cardLayout = CardLayout()
      val cardPanel = JPanel(cardLayout).apply {
        preferredSize = JBUI.size(640, 480)
        for (panel in panels) {
          add(panel.getFirstWizardStep().component, panel.panelName)
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
          val newSelectedWizardStep = selectedValue.getFirstWizardStep()
          selectedWizardStep = newSelectedWizardStep
          cardLayout.show(cardPanel, selectedValue.panelName)

          southPanel?.let {
            if (newSelectedWizardStep.hasNext()) {
              navigationPanelCardLayout?.show(it, WIZARD_CARD_PANE)
              rootPane.defaultButton = nextButton.value
              previousButton.value.isEnabled = false
            }
            else {
              navigationPanelCardLayout?.show(it, REGULAR_CARD_PANE)
              rootPane.defaultButton = getButton(okAction)
            }
          }
        }
        selectedPanel = panels.getOrNull(0)
        selectedIndex = 0
      }

      firstComponent = cardsList
      secondComponent = cardPanel
    }
  }

  private fun createVirtualEnvPanel(project: Project?,
                                    existingSdks: List<Sdk>,
                                    newProjectPath: String?): PyAddSdkPanel {
    val newVirtualEnvPanel = when {
      allowCreatingNewEnvironments(project) -> PyAddNewVirtualEnvPanel(project, existingSdks, newProjectPath)
      else -> null
    }
    val existingVirtualEnvPanel = PyAddExistingVirtualEnvPanel(project, existingSdks, newProjectPath)
    val panels = listOf(newVirtualEnvPanel,
                        existingVirtualEnvPanel)
      .filterNotNull()
    val defaultPanel = when {
      detectVirtualEnvs(project, existingSdks).any { it.isAssociatedWithProject(project) } -> existingVirtualEnvPanel
      newVirtualEnvPanel != null -> newVirtualEnvPanel
      else -> existingVirtualEnvPanel
    }
    return PyAddSdkGroupPanel("Virtualenv environment", PythonIcons.Python.Virtualenv, panels, defaultPanel)
  }

  private fun createAnacondaPanel(project: Project?): PyAddSdkPanel {
    val newCondaEnvPanel = when {
      allowCreatingNewEnvironments(project) -> PyAddNewCondaEnvPanel(project, existingSdks, newProjectPath)
      else -> null
    }
    val panels = listOf(newCondaEnvPanel,
                        PyAddExistingCondaEnvPanel(project, existingSdks, newProjectPath))
      .filterNotNull()
    return PyAddSdkGroupPanel("Conda environment", PythonIcons.Python.Anaconda, panels, panels[0])
  }

  private fun onSelected() {
    // clean `mainPanel` from old wizards
    for (component in mainPanel.components.copyOf()) {
      mainPanel.remove(component)
    }
  }

  /**
   * Navigates to the next step of the current wizard view.
   */
  private fun onNext() {
    selectedWizardStep?.let {
      val newWizardStep = it.next()

      // sliding effect
      val stepContent = newWizardStep.component
      val stepContentName = stepContent.hashCode().toString()

      mainPanel.add(stepContentName, stepContent)
      (mainPanel.layout as JBCardLayout).swipe(mainPanel, stepContentName, JBCardLayout.SwipeDirection.FORWARD)

      previousButton.value.isEnabled = true

      selectedWizardStep = newWizardStep
    }
  }

  /**
   * Navigates to the previous step of the current wizard view.
   */
  private fun onPrevious() {
    selectedWizardStep?.let {
      val previousWizardStep = it.previous()

      // sliding effect
      if (previousWizardStep.hasPrevious()) {
        val stepContent = previousWizardStep.component
        val stepContentName = stepContent.hashCode().toString()

        (mainPanel.layout as JBCardLayout).swipe(mainPanel, stepContentName, JBCardLayout.SwipeDirection.BACKWARD)
      }
      else {
        // this is the first wizard step
        (mainPanel.layout as JBCardLayout).swipe(mainPanel, SPLITTER_COMPONENT, JBCardLayout.SwipeDirection.BACKWARD)
      }

      previousButton.value.isEnabled = previousWizardStep.hasPrevious()

      selectedWizardStep = previousWizardStep
    }
  }

  companion object {
    private fun allowCreatingNewEnvironments(project: Project?) =
      project != null || !PlatformUtils.isPyCharm() || PlatformUtils.isPyCharmEducational()

    private val SPLITTER_COMPONENT = "Splitter"

    private const val REGULAR_CARD_PANE = "Regular"

    private const val WIZARD_CARD_PANE = "Wizard"

    @JvmStatic
    fun create(project: Project?, existingSdks: List<Sdk>, newProjectPath: String?): PyAddSdkDialog {
      return PyAddSdkDialog(project = project, existingSdks = existingSdks, newProjectPath = newProjectPath).apply { init() }
    }
  }
}
