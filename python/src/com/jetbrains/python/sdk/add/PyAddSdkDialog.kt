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

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.Splitter
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.ui.popup.ListItemDescriptorAdapter
import com.intellij.openapi.util.Disposer
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
import com.jetbrains.python.sdk.add.PyAddSdkDialog.Companion.create
import com.jetbrains.python.sdk.add.PyAddSdkDialogFlowAction.*
import com.jetbrains.python.sdk.detectVirtualEnvs
import com.jetbrains.python.sdk.isAssociatedWithProject
import icons.PythonIcons
import java.awt.CardLayout
import java.awt.event.ActionEvent
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JPanel

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
    title = "Add Python Interpreter"
  }

  override fun createCenterPanel(): JComponent {
    val sdks = existingSdks
      .filter { it.sdkType is PythonSdkType && !PythonSdkType.isInvalid(it) }
      .sortedWith(PreferredSdkComparator())
    val panels = arrayListOf<PyAddSdkView>(createVirtualEnvPanel(project, sdks, newProjectPath),
                                           createAnacondaPanel(project),
                                           PyAddSystemWideInterpreterPanel(existingSdks))
    val extendedPanels = PyAddSdkProvider.EP_NAME.extensions
      .mapNotNull { it.createView(project = project, newProjectPath = newProjectPath, existingSdks = existingSdks).registerIfDisposable() }
    panels.addAll(extendedPanels)
    mainPanel.add(SPLITTER_COMPONENT_CARD_PANE, createCardSplitter(panels))
    return mainPanel
  }

  private fun <T> T.registerIfDisposable(): T = apply { (this as? Disposable)?.let { Disposer.register(disposable, it) } }

  private var navigationPanelCardLayout: CardLayout? = null

  private var southPanel: JPanel? = null

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

    return doCreateSouthPanel(leftButtons = listOf(),
                                                           rightButtons = listOf(previousButton.value, nextButton.value,
                                                                                 cancelButton.value))
  }

  private val nextAction: Action = object : DialogWrapperAction("Next") {
    override fun doAction(e: ActionEvent) {
      selectedPanel?.let {
        if (it.actions.containsKey(NEXT)) onNext()
        else if (it.actions.containsKey(FINISH)) {
          onFinish()
        }
      }
    }
  }

  private val nextButton = lazy { createJButtonForAction(nextAction) }

  private val previousAction = object : DialogWrapperAction("Previous") {
    override fun doAction(e: ActionEvent) = onPrevious()
  }

  private val previousButton = lazy { createJButtonForAction(previousAction) }

  private val cancelButton = lazy { createJButtonForAction(cancelAction) }

  override fun postponeValidation() = false

  override fun doValidateAll(): List<ValidationInfo> = selectedPanel?.validateAll() ?: emptyList()

  fun getOrCreateSdk(): Sdk? = selectedPanel?.getOrCreateSdk()

  private fun createCardSplitter(panels: List<PyAddSdkView>): Splitter {
    this.panels = panels
    return Splitter(false, 0.25f).apply {
      val cardLayout = CardLayout()
      val cardPanel = JPanel(cardLayout).apply {
        preferredSize = JBUI.size(640, 480)
        for (panel in panels) {
          add(panel.component, panel.panelName)

          panel.addStateListener(object : PyAddSdkStateListener {
            override fun onComponentChanged() {
              show(mainPanel, panel.component)

              selectedPanel?.let { updateWizardActionButtons(it) }
            }

            override fun onActionsStateChanged() {
              selectedPanel?.let { updateWizardActionButtons(it) }
            }
          })
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

          southPanel?.let {
            if (selectedValue.actions.containsKey(NEXT)) {
              navigationPanelCardLayout?.show(it, WIZARD_CARD_PANE)
              rootPane.defaultButton = nextButton.value

              updateWizardActionButtons(selectedValue)
            }
            else {
              navigationPanelCardLayout?.show(it, REGULAR_CARD_PANE)
              rootPane.defaultButton = getButton(okAction)
            }
          }

          selectedValue.onSelected()
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

  /**
   * Navigates to the next step of the current wizard view.
   */
  private fun onNext() {
    selectedPanel?.let {
      it.next()

      // sliding effect
      swipe(mainPanel, it.component, JBCardLayout.SwipeDirection.FORWARD)

      updateWizardActionButtons(it)
    }
  }

  /**
   * Navigates to the previous step of the current wizard view.
   */
  private fun onPrevious() {
    selectedPanel?.let {
      it.previous()

      // sliding effect
      if (it.actions.containsKey(PREVIOUS)) {
        val stepContent = it.component
        val stepContentName = stepContent.hashCode().toString()

        (mainPanel.layout as JBCardLayout).swipe(mainPanel, stepContentName, JBCardLayout.SwipeDirection.BACKWARD)
      }
      else {
        // this is the first wizard step
        (mainPanel.layout as JBCardLayout).swipe(mainPanel, SPLITTER_COMPONENT_CARD_PANE, JBCardLayout.SwipeDirection.BACKWARD)
      }

      updateWizardActionButtons(it)
    }
  }

  /**
   * Tries to create the SDK and closes the dialog if the creation succeeded.
   *
   * @see [doOKAction]
   */
  override fun doOKAction() {
    try {
      selectedPanel?.complete()
    }
    catch (e: CreateSdkInterrupted) {
      return
    }
    catch (e: Exception) {
      val cause = ExceptionUtil.findCause(e, PyExecutionException::class.java)
      if (cause == null) {
        Messages.showErrorDialog(e.localizedMessage, "Error")
      }
      else {
        showProcessExecutionErrorDialog(project, cause)
      }
      return
    }

    close(OK_EXIT_CODE)
  }

  private fun onFinish() {
    doOKAction()
  }

  private fun updateWizardActionButtons(it: PyAddSdkView) {
    previousButton.value.isEnabled = false

    it.actions.forEach { (action, isEnabled) ->
      val actionButton = when (action) {
        PREVIOUS -> previousButton.value
        NEXT -> nextButton.value.apply { text = "Next" }
        FINISH -> nextButton.value.apply { text = "Finish" }
        else -> null
      }
      actionButton?.isEnabled = isEnabled
    }
  }

  companion object {
    private fun allowCreatingNewEnvironments(project: Project?) =
      project != null || !PlatformUtils.isPyCharm() || PlatformUtils.isPyCharmEducational()

    private const val SPLITTER_COMPONENT_CARD_PANE = "Splitter"

    private const val REGULAR_CARD_PANE = "Regular"

    private const val WIZARD_CARD_PANE = "Wizard"

    @JvmStatic
    fun create(project: Project?, existingSdks: List<Sdk>, newProjectPath: String?): PyAddSdkDialog {
      return PyAddSdkDialog(project = project, existingSdks = existingSdks, newProjectPath = newProjectPath).apply { init() }
    }
  }
}

class CreateSdkInterrupted: Exception()
