// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger.containerview

import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.observable.properties.AtomicBooleanProperty
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.EditorTextField
import com.intellij.ui.JBColor
import com.intellij.ui.dsl.builder.*
import com.intellij.util.TextFieldCompletionProvider
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PythonFileType
import com.jetbrains.python.debugger.*
import com.jetbrains.python.debugger.array.AbstractDataViewTable
import com.jetbrains.python.debugger.array.AsyncArrayTableModel
import com.jetbrains.python.debugger.array.JBTableWithRowHeaders
import com.jetbrains.python.debugger.statistics.PyDataViewerCollector
import java.awt.BorderLayout
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.util.concurrent.CopyOnWriteArrayList
import javax.swing.JEditorPane
import javax.swing.JPanel

open class PyDataViewerPanel(@JvmField protected val project: Project, val frameAccessor: PyFrameAccessor) :
  JPanel(BorderLayout()), Disposable {

  val sliceTextField = createEditorField()

  protected val tablePanel = JPanel(BorderLayout())

  protected var table: AbstractDataViewTable? = null

  private var formatTextField: EditorTextField = createEditorField()

  private var colored = PyDataView.isColoringEnabled(project)

  private val listeners = CopyOnWriteArrayList<Listener>()

  var originalVarName: @NlsSafe String? = null
    private set

  private var modifiedVarName: String? = null

  protected var debugValue: PyDebugValue? = null

  val format: String
    get() {
      val format = formatTextField.getText()
      return format.ifEmpty { "%" }
    }

  var isColored: Boolean
    get() = colored
    set(state) {
      colored = state
      val table = table
      if (table != null && !table.isEmpty) {
        (table.getDefaultRenderer(table.getColumnClass(0)) as ColoredCellRenderer).setColored(state)
        table.repaint()
      }
    }

  private val model: AsyncArrayTableModel?
    get() {
      return table?.model as? AsyncArrayTableModel
    }

  var isModified = false
    private set

  private lateinit var errorLabel: Cell<JEditorPane>

  protected val showFooter = AtomicBooleanProperty(true)

  init {
    border = JBUI.Borders.empty(5)

    PyDataViewCompletionProvider().apply(sliceTextField)

    val panel = panel {
      row { cell(tablePanel).align(Align.FILL).resizableColumn() }.resizableRow()
      row {
        cell(sliceTextField).align(AlignX.FILL).resizableColumn()
        label(PyBundle.message("form.data.viewer.format"))
        cell(formatTextField)
      }.visibleIf(showFooter)
      row { errorLabel = text("").apply { component.setForeground(JBColor.RED) } }
    }

    add(panel, BorderLayout.CENTER)

    setupChangeListener()
  }

  override fun dispose() = Unit

  private fun isVariablePresentInStack(): Boolean {
    val values = frameAccessor.loadFrame(null) ?: return true
    for (i in 0 until values.size()) {
      if (values.getValue(i) == debugValue) {
        return true
      }
    }
    return false
  }

  private fun setupChangeListener() {
    frameAccessor.addFrameListener(object : PyFrameListener {
      override fun frameChanged() {
        debugValue ?: return
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
    if (isModified) {
      apply(modifiedVarName, true)
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
    if (oldValue != null && !oldValue.isTemporary || sliceTextField.getText().isEmpty()) {
      return
    }
    val newValue = getDebugValue(sliceTextField.getText(), false, false)
    if (newValue != null) {
      model.debugValue = newValue
    }
  }

  protected open fun getOrCreateMainTable(): AbstractDataViewTable {
    val mainTable = JBTableWithRowHeaders(PyDataView.isAutoResizeEnabled(project))
    tablePanel.add(mainTable.scrollPane, BorderLayout.CENTER)
    table = mainTable
    return mainTable
  }

  private fun createEditorField(): EditorTextField {
    return object : EditorTextField(EditorFactory.getInstance().createDocument(""), project, PythonFileType.INSTANCE, false, true) {
      override fun createEditor(): EditorEx {
        val editor = super.createEditor()
        editor.settings.additionalColumnsCount = 5
        editor.getContentComponent().addKeyListener(object : KeyAdapter() {
          override fun keyPressed(e: KeyEvent) {
            if (e.keyCode == KeyEvent.VK_ENTER) {
              apply(sliceTextField.getText(), false)
            }
          }
        })
        return editor
      }
    }
  }

  fun apply(name: String?, modifier: Boolean) {
    ApplicationManager.getApplication().executeOnPooledThread {
      val debugValue = getDebugValue(name, true, modifier)
      ApplicationManager.getApplication().invokeLater { debugValue?.let { apply(it, modifier) } }
    }

    PyDataViewerCollector.slicingApplied.log()
  }

  open fun apply(debugValue: PyDebugValue, modifier: Boolean) {
    errorLabel.visible(false)
    val type = debugValue.type
    val strategy = DataViewStrategy.getStrategy(type)
    if (strategy == null) {
      setError(PyBundle.message("debugger.data.view.type.is.not.supported", type), modifier)
      return
    }
    ApplicationManager.getApplication().executeOnPooledThread {
      try {
        doStrategyInitExecution(debugValue.frameAccessor, strategy)

        // Currently does not support pandas dataframes.
        val arrayChunk = debugValue.frameAccessor.getArrayItems(debugValue, 0, 0, 0, 0, format)
        ApplicationManager.getApplication().invokeLater {
          updateUI(arrayChunk, debugValue, strategy, modifier)
          isModified = modifier
          this.debugValue = debugValue
        }
      }
      catch (e: IllegalArgumentException) {
        ApplicationManager.getApplication().invokeLater { setError(e.localizedMessage, modifier) } //NON-NLS
      }
      catch (e: PyDebuggerException) {
        LOG.error(e)
      }
    }
  }

  @Throws(PyDebuggerException::class)
  protected open fun doStrategyInitExecution(frameAccessor: PyFrameAccessor, strategy: DataViewStrategy) = Unit

  // Chunk currently could be null when we are trying to view, for example,  pandas dataframe.
  protected open fun updateTabNameAndSliceField(chunk: ArrayChunk?, originalDebugValue: PyDebugValue, modifier: Boolean) {
    // Debugger generates a temporary name for every slice evaluation, so we should select a correct name for it
    val debugValue = chunk?.value
    val realName = if (debugValue == null || debugValue.name == originalDebugValue.tempName) originalDebugValue.name else chunk.slicePresentation
    var shownName = realName
    if (modifier && originalVarName != shownName) {
      shownName = String.format(MODIFIED_VARIABLE_FORMAT, originalVarName)
    }
    else {
      originalVarName = realName
    }
    sliceTextField.setText(originalVarName)

    // Modifier flag means that variable changes are temporary
    modifiedVarName = realName
    if (sliceTextField.editor != null) {
      sliceTextField.getCaretModel().moveToOffset(originalVarName!!.length)
    }
    for (listener in listeners) {
      listener.onNameChanged(shownName)
    }

    if (chunk != null) {
      formatTextField.text = chunk.format
    }
  }

  protected open fun updateUI(chunk: ArrayChunk, originalDebugValue: PyDebugValue,
                              strategy: DataViewStrategy, modifier: Boolean) {
    val debugValue = chunk.value
    val model = strategy.createTableModel(chunk.rows, chunk.columns, this, debugValue)
    model.addToCache(chunk)
    UIUtil.invokeLaterIfNeeded {
      val table = table ?: getOrCreateMainTable()
      table.setModel(model, modifier)

      updateTabNameAndSliceField(chunk, originalDebugValue, modifier)

      val cellRenderer = strategy.createCellRenderer(Double.MIN_VALUE, Double.MAX_VALUE, chunk)
      cellRenderer.setColored(colored)
      model.fireTableDataChanged()
      model.fireTableCellUpdated(0, 0)
      if (table.columnCount > 0) {
        table.setDefaultRenderer(table.getColumnClass(0), cellRenderer)
      }
      table.setShowColumns(strategy.showColumnHeader())
    }
  }

  private fun getDebugValue(expression: @NlsSafe String?, pooledThread: Boolean, modifier: Boolean): PyDebugValue? {
    return try {
      val value = frameAccessor.evaluate(expression, false, true)
      if (value == null || value.isErrorOnEval) {
        val runnable = Runnable {
          setError(if (value != null && value.value != null) value.value!!
                   else PyBundle.message("debugger.data.view.failed.to.evaluate.expression", expression), modifier)
        }
        if (pooledThread) {
          ApplicationManager.getApplication().invokeLater(runnable)
        }
        else {
          runnable.run()
        }
        return null
      }
      value
    }
    catch (e: PyDebuggerException) {
      val runnable = Runnable { setError(e.getTracebackError(), modifier) } //NON-NLS
      if (pooledThread) {
        ApplicationManager.getApplication().invokeLater(runnable)
      }
      else {
        runnable.run()
      }
      null
    }
  }

  fun resize(autoResize: Boolean) {
    table?.setAutoResize(autoResize)
    apply(sliceTextField.getText(), false)
  }

  private fun setError(text: @NlsContexts.Label String, modifier: Boolean) {
    errorLabel.visible(true)
    errorLabel.text(if (modifier) PyBundle.message("debugger.dataviewer.modifier.error", text) else text)
    if (!modifier) {
      table?.setEmpty()
      for (listener in listeners) {
        listener.onNameChanged(PyBundle.message("debugger.data.view.empty.tab"))
      }
    }
  }

  fun addListener(listener: Listener) {
    listeners.add(listener)
  }

  fun interface Listener {
    fun onNameChanged(name: @NlsContexts.TabTitle String)
  }

  private inner class PyDataViewCompletionProvider : TextFieldCompletionProvider() {
    override fun addCompletionVariants(text: String, offset: Int, prefix: String, result: CompletionResultSet) {
      val values = availableValues.sortedBy { obj: PyDebugValue -> obj.name }
      for (i in values.indices) {
        val value = values[i]
        val element = LookupElementBuilder.create(value.name).withTypeText(value.type, true)
        result.addElement(PrioritizedLookupElement.withPriority(element, -i.toDouble()))
      }
    }

    private val availableValues: List<PyDebugValue>
      get() {
        val values: MutableList<PyDebugValue> = ArrayList()
        try {
          val list = frameAccessor.loadFrame(null) ?: return values
          for (i in 0 until list.size()) {
            val value = list.getValue(i) as PyDebugValue
            val type = value.type
            if (DataViewStrategy.getStrategy(type) != null) {
              values.add(value)
            }
          }
        }
        catch (e: Exception) {
          LOG.error(e)
        }
        return values
      }
  }

  open fun closeEditorTabs() {}

  companion object {
    private val LOG = Logger.getInstance(PyDataViewerPanel::class.java)
    private const val MODIFIED_VARIABLE_FORMAT = "%s*"
  }
}
