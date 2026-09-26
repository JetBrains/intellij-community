package com.intellij.python.processOutput.frontend.ui.components

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.python.processOutput.frontend.ProcessOutputBundle.message
import com.intellij.python.processOutput.frontend.ui.ProcessOutputUiContext
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.SearchTextField
import com.intellij.ui.SideBorder
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.event.DocumentEvent

internal class ProcessTreeSection(private val uiContext: ProcessOutputUiContext) {
  private val processTree = ProcessTree(uiContext)

  val component: JComponent
    field = JPanel(BorderLayout())

  init {
    component.add(toolbar(), BorderLayout.NORTH)
    component.add(processTree.component, BorderLayout.CENTER)
  }

  private fun toolbar(): JPanel {
    val panel = JPanel(BorderLayout())

    panel.border = IdeBorderFactory.createBorder(SideBorder.BOTTOM)
    panel.add(searchField(), BorderLayout.CENTER)
    panel.add(actionToolbar().component, BorderLayout.LINE_END)

    return panel
  }

  private fun searchField(): SearchTextField {
    val searchTextField =
      SearchTextField(
        true,
        true,
        SEARCH_HISTORY_PROPERTY_NAME
      )

    searchTextField.name = Naming.SEARCH_FIELD_NAME
    searchTextField.setHistorySize(SEARCH_HISTORY_SIZE)

    searchTextField.textEditor.border = JBUI.Borders.emptyLeft(Styling.SEARCH_WEST_OFFSET)
    searchTextField.textEditor.isOpaque = true
    searchTextField.textEditor.emptyText.text = message("process.output.tree.search.placeholder")
    searchTextField.addDocumentListener(object : DocumentAdapter() {
      override fun textChanged(e: DocumentEvent) {
        if (uiContext.controller.treeSectionState.searchQuery.value == searchTextField.text) {
          return
        }

        uiContext.controller.search(searchTextField.text)
      }
    })

    uiContext.coroutineScope.launch(Dispatchers.EDT) {
      uiContext.controller.treeSectionState.searchQuery.collect {
        if (searchTextField.text == it) {
          return@collect
        }

        searchTextField.text = it
      }
    }

    return searchTextField
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
        name = message("process.output.tree.buttons.displayOptions"),
        state = uiContext.controller.treeSectionState.filters,
        onFilterItemToggled = { filterItem, enabled ->
          uiContext.controller.onTreeFilterItemToggled(filterItem, enabled)
        }
      )
    )
    group.add(
      DumbAwareAction.create(message("process.output.tree.buttons.expandAll"), AllIcons.Actions.Expandall) {
        processTree.expandAll()
      }
    )
    group.add(
      DumbAwareAction.create(message("process.output.tree.buttons.collapseAll"), AllIcons.Actions.Collapseall) {
        processTree.collapseAll()
      }
    )

    return group
  }

  private object Styling {
    const val SEARCH_WEST_OFFSET = 6
  }

  private object Naming {
    const val SEARCH_FIELD_NAME = "Python.ProcessOutput.Tree.SearchField"
  }

  companion object {
    const val SEARCH_HISTORY_PROPERTY_NAME = "ProcessOutputToolWindow.history"
    const val SEARCH_HISTORY_SIZE = 10
  }
}
