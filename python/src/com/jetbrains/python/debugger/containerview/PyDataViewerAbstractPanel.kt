// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger.containerview

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.EditorTextField
import com.jetbrains.python.PyBundle
import com.jetbrains.python.debugger.ArrayChunk
import com.jetbrains.python.debugger.PyDebugValue
import com.jetbrains.python.debugger.PyDebuggerException
import com.jetbrains.python.debugger.PyFrameAccessor
import com.jetbrains.python.debugger.statistics.PyDataViewerCollector
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import java.util.concurrent.CopyOnWriteArrayList
import javax.swing.JComponent
import javax.swing.JPanel

abstract class PyDataViewerAbstractPanel(
  val dataViewerModel: PyDataViewerModel,
  val isPanelFromFactory: Boolean = false,
) : JPanel(BorderLayout()), Disposable {

  /**
   * Represents a formatting string used for specifying format.
   */
  abstract var formatValueFromUI: String

  /**
   * Represents a slicing string used for slicing data in a viewer panel.
   * For example, np_array[0] or df['column_1'].
   */
  protected abstract var slicingValueFromUI: String

  abstract var isColoredValueFromUI: Boolean

  abstract val slicingTextField: EditorTextField

  abstract var topToolbar: JPanel?

  protected val listeners: CopyOnWriteArrayList<OnNameChangedListener> = CopyOnWriteArrayList<OnNameChangedListener>()

  protected abstract fun setError(text: @NlsContexts.Label String, modifier: Boolean)

  protected abstract fun setupDataProvider()

  protected abstract fun updateUI(chunk: ArrayChunk, originalDebugValue: PyDebugValue, strategy: DataViewStrategy, modifier: Boolean)

  abstract fun createTable(originalDebugValue: PyDebugValue? = null, chunk: ArrayChunk? = null): JComponent

  abstract fun recreateTable()

  protected fun onEnterPressed(commandSource: TextFieldCommandSource) {
    apply(commandSource)
  }

  fun apply(commandSource: TextFieldCommandSource) {
    dataViewerModel.format = formatValueFromUI
    dataViewerModel.slicing = slicingValueFromUI
    dataViewerModel.isColored = isColoredValueFromUI

    apply(dataViewerModel.slicing, false, commandSource)
  }

  fun apply(name: String?, modifier: Boolean, commandSource: TextFieldCommandSource? = null) {
    ApplicationManager.getApplication().executeOnPooledThread {
      val debugValue = getDebugValue(name, modifier)
      ApplicationManager.getApplication().invokeLater { debugValue?.let { apply(it, modifier, commandSource) } }
    }
  }

  fun apply(debugValue: PyDebugValue, modifier: Boolean, commandSource: TextFieldCommandSource? = null) {
    if (!modifier) {
      when (commandSource) {
        TextFieldCommandSource.SLICING -> PyDataViewerCollector.logDataSlicingApplied(isPanelFromFactory)
        TextFieldCommandSource.FORMATTING -> PyDataViewerCollector.logDataFormattingApplied(isPanelFromFactory)
        else -> Unit
      }

      val dimensions = getValueDimensions(debugValue)
      PyDataViewerCollector.logDataOpened(dataViewerModel.project, debugValue.type,
                                          dimensions?.size,
                                          dimensions?.getOrNull(0) ?: 0,
                                          dimensions?.getOrNull(1) ?: 0,
                                          isNewTable = isPanelFromFactory)
    }

    val type = debugValue.type
    val strategy = DataViewStrategy.getStrategy(type)
    if (strategy == null) {
      setError(PyBundle.message("debugger.data.view.type.is.not.supported", type), modifier)
      return
    }

    ApplicationManager.getApplication().executeOnPooledThread {
      try {
        doStrategyInitExecution(debugValue.frameAccessor, strategy)
        val arrayChunk = debugValue.frameAccessor.getArrayItems(debugValue, 0, 0, 0, 0, formatValueFromUI)
        ApplicationManager.getApplication().invokeLater {
          updateUI(arrayChunk, debugValue, strategy, modifier)
          dataViewerModel.isModified = modifier
          dataViewerModel.debugValue = debugValue
        }
      }
      catch (e: IllegalArgumentException) {
        ApplicationManager.getApplication().invokeLater { setError(e.localizedMessage, modifier) } //NON-NLS
      }
      catch (e: PyDebuggerException) {
        thisLogger().error(e)
      }
      catch (e: Exception) {
        if (e.message?.contains("Numpy is not available") == true) {
          setError(PyBundle.message("debugger.data.view.numpy.is.not.available", type), modifier)
        }
        thisLogger().error("PyDataViewer.apply: Numpy is not available", e)
      }
    }
  }

  /**
   * PyDebugValue shape in case of arrays could be like (10, 14, 23),
   * and we can extract these dimensions for analysis and logging.
   */
  private fun getValueDimensions(debugValue: PyDebugValue): List<Int>? {
    val shape = debugValue.shape?.takeIf { it.startsWith("(") && it.endsWith(")") } ?: return null
    return shape
      .removeSurrounding("(", ")")
      .split(",")
      .filter { it.isNotEmpty() }
      .mapNotNull { it.trim().toIntOrNull() }
      .takeIf { it.size == shape.count { ch -> ch == ',' } + 1 }
  }

  @Throws(PyDebuggerException::class)
  protected fun doStrategyInitExecution(frameAccessor: PyFrameAccessor, strategy: DataViewStrategy) {
    val execString = strategy.initExecuteString ?: return
    frameAccessor.evaluate(execString, true, false)
  }

  protected fun updateTabNameSlicingFieldAndFormatField(chunk: ArrayChunk?, originalDebugValue: PyDebugValue, modifier: Boolean) {
    // Debugger generates a temporary name for every slice evaluation, so we should select a correct name for it
    val debugValue = chunk?.value
    val realName = if (debugValue == null || debugValue.name == originalDebugValue.tempName) originalDebugValue.name else chunk.slicePresentation
    var shownName = realName
    if (modifier && dataViewerModel.originalVarName != shownName) {
      shownName = String.format(MODIFIED_VARIABLE_FORMAT, dataViewerModel.originalVarName)
    }
    else {
      dataViewerModel.originalVarName = realName
    }
    dataViewerModel.originalVarName?.let { slicingValueFromUI = it }

    // Modifier flag means that variable changes are temporary
    dataViewerModel.modifiedVarName = realName

    for (listener in listeners) {
      listener.onNameChanged(shownName)
    }

    if (chunk != null) {
      formatValueFromUI = chunk.format
    }
  }

  protected fun getDebugValue(expression: @NlsSafe String?, modifier: Boolean): PyDebugValue? {
    return try {
      val debugValue = dataViewerModel.frameAccessor.evaluate(expression, false, true)
      if (debugValue == null || debugValue.isErrorOnEval) {
        ApplicationManager.getApplication().invokeLater {
          val debugValueExpression = debugValue.value
          val errorText = if (debugValue != null && debugValueExpression != null) {
            debugValueExpression
          } else {
            PyBundle.message("debugger.data.view.failed.to.evaluate.expression", expression)
          }

          setError(errorText, modifier)
        }
        null
      }
      else {
        debugValue
      }
    }
    catch (e: PyDebuggerException) {
      ApplicationManager.getApplication().invokeLater { setError(e.getTracebackError(), modifier) } //NON-NLS
      null
    }
  }

  fun addListener(onNameChangedListener: OnNameChangedListener) {
    listeners.add(onNameChangedListener)
  }

  protected fun composeErrorMessage(text: @NlsSafe String, modifier: Boolean): @Nls String {
    return if (modifier) PyBundle.message("debugger.dataViewer.modifier.error", text) else text
  }

  override fun dispose(): Unit = Unit

  fun interface OnNameChangedListener {
    fun onNameChanged(name: @NlsContexts.TabTitle String)
  }

  companion object {
    private const val MODIFIED_VARIABLE_FORMAT = "%s*"
  }
}