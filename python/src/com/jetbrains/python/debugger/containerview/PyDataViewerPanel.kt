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
import com.intellij.util.ui.UIUtil
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PythonFileType
import com.jetbrains.python.debugger.*
import com.jetbrains.python.debugger.array.AbstractDataViewTable
import com.jetbrains.python.debugger.array.AsyncArrayTableModel
import com.jetbrains.python.debugger.array.JBTableWithRowHeaders
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.util.concurrent.CopyOnWriteArrayList
import javax.swing.BorderFactory
import javax.swing.JEditorPane
import javax.swing.JPanel

open class PyDataViewerPanel(@JvmField protected val project: Project, val frameAccessor: PyFrameAccessor) :
  JPanel(BorderLayout()), Disposable {

  protected val tablePanel = JPanel(BorderLayout())

  protected var table: AbstractDataViewTable? = null

  protected val sliceTextFieldOldTable: EditorTextField = createEditorField(TextFieldCommandSource.SLICING)

  protected var formatTextFieldOldTable: EditorTextField = createEditorField(TextFieldCommandSource.FORMATTING)

  private var colored: Boolean = PyDataView.isColoringEnabled(project)

  private val listeners = CopyOnWriteArrayList<Listener>()

  var originalVarName: @NlsSafe String? = null
    private set

  protected var modifiedVarName: String? = null

  protected var debugValue: PyDebugValue? = null

  /**
   * Represents a formatting string used for specifying format.
   *
   * This field is needed to synchronize old and new tables
   * regarding the actual formatting value.
   */
  open var format: String = ""
    get() {
      val format = formatTextFieldOldTable.getText()
      return format.ifEmpty { "%" }
    }
    protected set(value) {
      field = value
      formatTextFieldOldTable.text = value
    }

  /**
   * Represents a slicing string used for slicing data in a viewer panel.
   * For example, numpy_array[:, 0] or df['column_1'].
   *
   * This field is needed to synchronize old and new tables
   * regarding the actual slicing value.
   */
  open var slicing: String = ""
    get() {
      val slicing = sliceTextFieldOldTable.getText()
      return slicing.ifEmpty { "None" }
    }
    protected set(value) {
      field = value
      sliceTextFieldOldTable.text = value
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

  var isModified: Boolean = false
    protected set

  private lateinit var errorLabel: Cell<JEditorPane>

  protected val isSlicingAndFormattingOldPanelsVisible: AtomicBooleanProperty = AtomicBooleanProperty(true)

  init {
    PyDataViewCompletionProvider().apply(sliceTextFieldOldTable)

    val panel = panel {
      row {
        cell(tablePanel).align(Align.FILL).resizableColumn()
      }.resizableRow()
      row {
        cell(sliceTextFieldOldTable).align(AlignX.FILL).resizableColumn()
        label(PyBundle.message("form.data.viewer.format"))
        cell(formatTextFieldOldTable)
      }.visibleIf(isSlicingAndFormattingOldPanelsVisible)
      row {
        errorLabel = text("").apply { component.setForeground(JBColor.RED) }
      }
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
    if (oldValue != null && !oldValue.isTemporary || slicing.isEmpty()) {
      return
    }
    val newValue = getDebugValue(slicing, false, false)
    if (newValue != null) {
      model.debugValue = newValue
    }
  }

  protected open fun getOrCreateMainTable(): AbstractDataViewTable {
    val mainTable = JBTableWithRowHeaders(PyDataView.isAutoResizeEnabled(project))
    mainTable.scrollPane.border = BorderFactory.createEmptyBorder()
    tablePanel.add(mainTable.scrollPane, BorderLayout.CENTER)
    table = mainTable
    return mainTable
  }

  private fun createEditorField(commandSource: TextFieldCommandSource): EditorTextField {
    return object : EditorTextField(EditorFactory.getInstance().createDocument(""), project, PythonFileType.INSTANCE, false, true) {
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

  protected fun onEnterPressed(commandSource: TextFieldCommandSource) {
    apply(slicing, false, commandSource)
  }

  fun apply(name: String?, modifier: Boolean, commandSource: TextFieldCommandSource? = null) {
    ApplicationManager.getApplication().executeOnPooledThread {
      val debugValue = getDebugValue(name, true, modifier)
      ApplicationManager.getApplication().invokeLater { debugValue?.let { apply(it, modifier, commandSource) } }
    }
  }

  open fun apply(debugValue: PyDebugValue, modifier: Boolean, commandSource: TextFieldCommandSource? = null) {
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
      catch (e: Exception) {
        if (e.message?.let { "Numpy is not available" in it } == true) {
          setError(PyBundle.message("debugger.data.view.numpy.is.not.available", type), modifier)
        }
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
      @Suppress("HardCodedStringLiteral") // This is just format like %s and cannot be i18.
      shownName = String.format(MODIFIED_VARIABLE_FORMAT, originalVarName)
    }
    else {
      originalVarName = realName
    }
    originalVarName?.let { slicing = it }

    // Modifier flag means that variable changes are temporary
    modifiedVarName = realName
    if (sliceTextFieldOldTable.editor != null) {
      sliceTextFieldOldTable.getCaretModel().moveToOffset(originalVarName!!.length)
    }
    for (listener in listeners) {
      listener.onNameChanged(shownName)
    }

    if (chunk != null) {
      format = chunk.format
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
    apply(slicing, false)
  }

  open fun setError(text: @NlsContexts.Label String, modifier: Boolean) {
    errorLabel.visible(true)
    errorLabel.text(composeErrorMessage(text, modifier))
    if (!modifier) {
      table?.setEmpty()
      for (listener in listeners) {
        listener.onNameChanged(PyBundle.message("debugger.data.view.empty.tab"))
      }
    }
  }

  protected fun composeErrorMessage(text: @NlsContexts.Label String, modifier: Boolean): @Nls String {
    return if (modifier) PyBundle.message("debugger.dataviewer.modifier.error", text) else text
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
