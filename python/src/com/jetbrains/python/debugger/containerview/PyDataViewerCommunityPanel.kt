// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger.containerview

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.toolbarLayout.ToolbarLayoutStrategy
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.EditorTextField
import com.intellij.ui.JBColor
import com.intellij.ui.components.TwoSideComponent
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.text
import com.intellij.util.ui.UIUtil
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PythonFileType
import com.jetbrains.python.debugger.ArrayChunk
import com.jetbrains.python.debugger.PyDebugValue
import com.jetbrains.python.debugger.PyFrameListener
import com.jetbrains.python.debugger.array.AbstractDataViewTable
import com.jetbrains.python.debugger.array.AsyncArrayTableModel
import com.jetbrains.python.debugger.array.JBTableWithRowHeaders
import java.awt.BorderLayout
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.BorderFactory
import javax.swing.JEditorPane
import javax.swing.JPanel

class PyDataViewerCommunityPanel(
  dataViewerModel: PyDataViewerModel,
) : PyDataViewerAbstractPanel(dataViewerModel, false) {

  private var table: AbstractDataViewTable? = null

  private val panelWithTable: JPanel = JPanel(BorderLayout())

  private var formatTextField: EditorTextField = createEditorField(DataViewerCommandSource.FORMATTING)

  override val slicingTextField: EditorTextField = createEditorField(DataViewerCommandSource.SLICING)

  override var topToolbar: JPanel? = null

  override var formatValueFromUI: String = ""
    get() {
      val format = formatTextField.text
      return format.ifEmpty { "%s" }
    }
    set(value) {
      field = value
      formatTextField.text = value
    }

  override var slicingValueFromUI: String = ""
    get() {
      val slicing = slicingTextField.text
      return slicing.ifEmpty { "None" }
    }
    set(value) {
      field = value
      slicingTextField.text = value
    }

  override var isColoredValueFromUI: Boolean
    get() = dataViewerModel.protectedColored
    set(value) {
      dataViewerModel.protectedColored = value
      val table = table
      if (table != null && !table.isEmpty) {
        (table.getDefaultRenderer(table.getColumnClass(0)) as ColoredCellRenderer).setColored(value)
        table.repaint()
      }
    }

  private val model: AsyncArrayTableModel?
    get() {
      return table?.model as? AsyncArrayTableModel
    }

  private lateinit var errorLabel: Cell<JEditorPane>

  init {
    dataViewerModel.PyDataViewerCompletionProvider().apply(slicingTextField)
    formatTextField.text = dataViewerModel.format

    add(panelWithTable, BorderLayout.CENTER)
    add(panel {
      row {
        cell(slicingTextField).align(AlignX.FILL).resizableColumn()
        label(PyBundle.message("form.data.viewer.format"))
        cell(formatTextField)
      }
      row {
        errorLabel = text("").apply { component.setForeground(JBColor.RED) }
      }
    }, BorderLayout.SOUTH)

    setupChangeListener()

    topToolbar = createAndSetupTopToolbar()
  }

  override fun recreateTable() {
    panelWithTable.removeAll()
    panelWithTable.add(createTable().scrollPane, BorderLayout.CENTER)
  }

  override fun setupDataProvider() {
    val toolbarDataProvider = DataProvider { dataId ->
      if (PY_DATA_VIEWER_COMMUNITY_PANEL_KEY.`is`(dataId)) table else null
    }

    PyDataViewerPanel.addDataProvider(this, toolbarDataProvider)
  }

  private fun createAndSetupTopToolbar(): JPanel {
    val actionManager = ActionManager.getInstance()

    /* Create the left toolbar */
    val leftActionGroup = DefaultActionGroup().apply {
      actionManager.getAction("ToggleDataViewColoring")?.let { add(it) }
    }
    val leftActionToolbar = actionManager.createActionToolbar("PyDataView", leftActionGroup, true).apply {
      targetComponent = panelWithTable
    }

    /* Create the right toolbar */
    val rightActionGroup = DefaultActionGroup().apply {
      listOf(
        "OpenInEditorAction",
        "ExportTableAction",
        "SwitchBetweenTableModesAction"
      ).forEach { actionId ->
        actionManager.getAction(actionId)?.let { add(it) }
      }
    }

    val rightActionToolbar = actionManager.createActionToolbar("PyDataView", rightActionGroup, true).apply {
      layoutStrategy = ToolbarLayoutStrategy.NOWRAP_STRATEGY // For removing the empty space on the right of the toolbar.
      targetComponent = panelWithTable
    }

    val twoSideComponent = TwoSideComponent(leftActionToolbar.component, rightActionToolbar.component)
    add(twoSideComponent, BorderLayout.BEFORE_FIRST_LINE)

    topToolbar = twoSideComponent

    return twoSideComponent
  }

  private fun isVariablePresentInStack(): Boolean {
    val values = dataViewerModel.frameAccessor.loadFrame(null) ?: return true
    for (i in 0 until values.size()) {
      if (values.getValue(i) == dataViewerModel.debugValue) {
        return true
      }
    }
    return false
  }

  private fun setupChangeListener() {
    dataViewerModel.frameAccessor.addFrameListener(object : PyFrameListener {
      override fun frameChanged() {
        dataViewerModel.debugValue ?: return
        ApplicationManager.getApplication().executeOnPooledThread {
          // Could be that in changed frames our value is missing. (PY-66235)
          if (isVariablePresentInStack()) {
            updateModel()
          }
        }
      }
    })
  }

  private fun updateModel() {
    val model = model ?: return
    model.invalidateCache()
    if (dataViewerModel.isModified) {
      apply(dataViewerModel.modifiedVarName, true)
    }
    else {
      updateDebugValue(model)
      ApplicationManager.getApplication().invokeLater {
        if (isShowing()) {
          model.fireTableDataChanged()
        }
      }
    }
  }

  private fun updateDebugValue(model: AsyncArrayTableModel) {
    val oldValue = model.debugValue
    if (oldValue != null && !oldValue.isTemporary || slicingValueFromUI.isEmpty()) {
      return
    }
    val newValue = getDebugValue(slicingValueFromUI, false)
    if (newValue != null) {
      model.debugValue = newValue
    }
  }

  override fun createTable(originalDebugValue: PyDebugValue?, chunk: ArrayChunk?): AbstractDataViewTable {
    val mainTable = JBTableWithRowHeaders(PyDataView.isAutoResizeEnabled(dataViewerModel.project))
    mainTable.scrollPane.border = BorderFactory.createEmptyBorder()

    panelWithTable.apply {
      add(mainTable.scrollPane, BorderLayout.CENTER)
      revalidate()
      repaint()
    }

    table = mainTable
    return mainTable
  }

  private fun createEditorField(commandSource: DataViewerCommandSource): EditorTextField {
    return object : EditorTextField(EditorFactory.getInstance().createDocument(""), dataViewerModel.project, PythonFileType.INSTANCE, false, true) {
      override fun createEditor(): EditorEx {
        val editor = super.createEditor()
        editor.settings.additionalColumnsCount = 5
        editor.getContentComponent().addKeyListener(object : KeyAdapter() {
          override fun keyPressed(e: KeyEvent) {
            if (e.keyCode == KeyEvent.VK_ENTER) {
              onEnterPressed(commandSource)
            }
          }
        })
        return editor
      }
    }
  }

  override fun updateUI(
    chunk: ArrayChunk,
    originalDebugValue: PyDebugValue,
    strategy: DataViewStrategy,
    modifier: Boolean,
  ) {
    errorLabel.visible(false)
    val debugValue = chunk.value
    val model = strategy.createTableModel(chunk.rows, chunk.columns, this, debugValue)
    model.addToCache(chunk)
    UIUtil.invokeLaterIfNeeded {
      val table = table ?: createTable()
      table.setModel(model, modifier)

      updateTabNameSlicingFieldAndFormatField(chunk, originalDebugValue, modifier)

      val cellRenderer = strategy.createCellRenderer(Double.MIN_VALUE, Double.MAX_VALUE, chunk)
      cellRenderer.setColored(dataViewerModel.protectedColored)
      model.fireTableDataChanged()
      model.fireTableCellUpdated(0, 0)
      if (table.columnCount > 0) {
        table.setDefaultRenderer(table.getColumnClass(0), cellRenderer)
      }
      table.setShowColumns(strategy.showColumnHeader())
    }
  }

  fun resize(autoResize: Boolean) {
    table?.setAutoResize(autoResize)
    apply(slicingValueFromUI, false)
  }

  override fun setError(text: @NlsContexts.Label String, modifier: Boolean) {
    errorLabel.visible(true)
    errorLabel.text(composeErrorMessage(text, modifier))
    if (!modifier) {
      table?.setEmpty()
      for (listener in listeners) {
        listener.onNameChanged(PyBundle.message("debugger.data.view.empty.tab"))
      }
    }
  }

  companion object {
    val PY_DATA_VIEWER_COMMUNITY_PANEL_KEY: DataKey<AbstractDataViewTable> = DataKey.create<AbstractDataViewTable>("PY_DATA_VIEWER_COMMUNITY_PANEL_KEY")
  }
}