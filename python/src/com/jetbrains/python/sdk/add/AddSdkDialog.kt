// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.sdk.add

import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.Splitter
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.ui.popup.ListItemDescriptorAdapter
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.JBCardLayout
import com.intellij.ui.components.JBList
import com.intellij.ui.popup.list.GroupedItemsListRenderer
import com.intellij.util.ExceptionUtil
import com.intellij.util.ui.JBUI
import com.jetbrains.python.packaging.PyExecutionException
import java.awt.CardLayout
import java.awt.event.ActionEvent
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JPanel

abstract class AddSdkDialog constructor(protected val project: Project?) : DialogWrapper(project) {
  /**
   * This is the main panel that supplies sliding effect for the wizard states.
   */
  private val mainPanel: JPanel = JPanel(JBCardLayout())

  private var selectedPanel: AddSdkView? = null
  private var panels: List<AddSdkView> = emptyList()

  override fun createCenterPanel(): JComponent {
    val panels = createViews()
    mainPanel.add(SPLITTER_COMPONENT_CARD_PANE, createCardSplitter(panels))
    return mainPanel
  }

  /**
   * The method is called *once* on the dialog initialization.
   */
  abstract fun createViews(): List<AddSdkView>

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
           lazyMessage = { "${AddSdkDialog::class.java} is not ready for ${DialogStyle.COMPACT} dialog style" })

    return doCreateSouthPanel(leftButtons = listOf(),
                              rightButtons = listOf(previousButton.value, nextButton.value,
                                                    cancelButton.value))
  }

  private val nextAction: Action = object : DialogWrapperAction("Next") {
    override fun doAction(e: ActionEvent) {
      selectedPanel?.let {
        if (it.actions.containsKey(DialogFlowAction.NEXT)) onNext()
        else if (it.actions.containsKey(DialogFlowAction.FINISH)) {
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

  override fun postponeValidation(): Boolean = false

  override fun doValidateAll(): List<ValidationInfo> = selectedPanel?.validateAll() ?: emptyList()

  fun getOrCreateSdk(): Sdk? = selectedPanel?.getOrCreateSdk()

  private fun createCardSplitter(panels: List<AddSdkView>): Splitter {
    this.panels = panels
    return Splitter(false, 0.25f).apply {
      val cardLayout = CardLayout()
      val cardPanel = JPanel(cardLayout).apply {
        preferredSize = JBUI.size(640, 480)
        for (panel in panels) {
          add(panel.component, panel.panelName)

          panel.addStateListener(object : AddSdkStateListener {
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
        val descriptor = object : ListItemDescriptorAdapter<AddSdkView>() {
          override fun getTextFor(value: AddSdkView) = StringUtil.toTitleCase(value.panelName)
          override fun getIconFor(value: AddSdkView) = value.icon
        }
        cellRenderer = object : GroupedItemsListRenderer<AddSdkView>(descriptor) {
          override fun createItemComponent() = super.createItemComponent().apply {
            border = JBUI.Borders.empty(4, 4, 4, 10)
          }
        }
        addListSelectionListener {
          selectedPanel = selectedValue
          cardLayout.show(cardPanel, selectedValue.panelName)

          southPanel?.let {
            if (selectedValue.actions.containsKey(DialogFlowAction.NEXT)) {
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
      if (it.actions.containsKey(DialogFlowAction.PREVIOUS)) {
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

  private fun updateWizardActionButtons(it: AddSdkView) {
    previousButton.value.isEnabled = false

    it.actions.forEach { (action, isEnabled) ->
      val actionButton = when (action) {
        DialogFlowAction.PREVIOUS -> previousButton.value
        DialogFlowAction.NEXT -> nextButton.value.apply { text = "Next" }
        DialogFlowAction.FINISH -> nextButton.value.apply { text = "Finish" }
        else -> null
      }
      actionButton?.isEnabled = isEnabled
    }
  }

  companion object {
    private const val SPLITTER_COMPONENT_CARD_PANE = "Splitter"

    private const val REGULAR_CARD_PANE = "Regular"

    private const val WIZARD_CARD_PANE = "Wizard"
  }
}