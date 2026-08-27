package com.intellij.python.processOutput.frontend.ui.components

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.python.processOutput.frontend.ProcessOutputBundle.message
import com.intellij.python.processOutput.frontend.ui.ProcessOutputUiContext
import com.intellij.python.processOutput.frontend.ui.shortenedCommandString
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.SideBorder
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPanel

internal class OutputSection(private val uiContext: ProcessOutputUiContext) {
  private val titleLabel: JBLabel = JBLabel()

  val component: JComponent
    field = JPanel(BorderLayout())

  init {
    titleLabel.font = JBFont.label().asBold()
    titleLabel.minimumSize = Dimension(0, titleLabel.minimumSize.height)

    uiContext.coroutineScope.launch(Dispatchers.EDT) {
      uiContext.controller.selectedProcess.collect { process ->
        titleLabel.text = process?.data?.shortenedCommandString ?: ""
      }
    }

    component.add(toolbar(), BorderLayout.NORTH)
    component.add(OutputConsole(uiContext).component, BorderLayout.CENTER)
  }

  private fun toolbar(): JPanel {
    val panel = JPanel(BorderLayout())

    panel.border = IdeBorderFactory.createBorder(SideBorder.BOTTOM)

    val titleWrapper = JPanel(BorderLayout())
    titleWrapper.isOpaque = false
    titleWrapper.border = JBUI.Borders.empty(Styling.TITLE_VERTICAL_PADDING, Styling.TITLE_HORIZONTAL_PADDING)
    titleWrapper.add(titleLabel, BorderLayout.CENTER)

    panel.add(titleWrapper, BorderLayout.CENTER)
    panel.add(actionToolbar().component, BorderLayout.EAST)

    return panel
  }

  private fun actionToolbar(): ActionToolbar {
    val actionToolbar =
      ActionManager
        .getInstance()
        .createActionToolbar(
          ActionPlaces.TOOLWINDOW_CONTENT,
          actionGroup(),
          true,
        )

    actionToolbar.targetComponent = uiContext.rootPanel

    return actionToolbar
  }

  private fun actionGroup(): DefaultActionGroup {
    val group = DefaultActionGroup()

    group.add(
      filterActionGroup(
        name = message("process.output.output.buttons.displayOptions"),
        state = uiContext.controller.outputSectionState.filters,
        onFilterItemToggled = { filterItem, enabled ->
          uiContext.controller.onOutputFilterItemToggled(filterItem, enabled)
        }
      )
    )

    group.add(
      object : DumbAwareAction(message("process.output.output.buttons.copyOutput")) {
        init {
          templatePresentation.icon = AllIcons.General.Copy
        }

        override fun update(e: AnActionEvent) {
          e.presentation.isEnabled = uiContext.controller.selectedProcess.value != null
        }

        override fun getActionUpdateThread(): ActionUpdateThread =
          ActionUpdateThread.BGT

        override fun actionPerformed(e: AnActionEvent) {
          uiContext.controller.selectedProcess.value?.also {
            uiContext.controller.copyOutputToClipboard(it)
          }
        }
      }
    )

    return group
  }

  private object Styling {
    const val TITLE_VERTICAL_PADDING = 2
    const val TITLE_HORIZONTAL_PADDING = 8
  }
}
