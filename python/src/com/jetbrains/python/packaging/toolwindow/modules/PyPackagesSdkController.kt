// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.toolwindow.modules

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.FileEditorManagerListener.FILE_EDITOR_MANAGER
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.util.coroutines.childScope
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBList
import com.intellij.util.asDisposable
import com.jetbrains.python.PyBundle
import com.jetbrains.python.TraceContext
import com.jetbrains.python.packaging.toolwindow.PyPackagingToolWindowService
import com.jetbrains.python.packaging.utils.PyPackageCoroutine
import com.intellij.python.pyproject.model.evolution.findPythonInterpreter
import com.intellij.python.pyproject.model.evolution.pythonInterpreters
import com.intellij.python.sdk.backend.PythonInterpreter
import com.intellij.python.sdk.backend.asInterpreterRef
import com.intellij.python.sdk.backend.asItem
import com.intellij.python.sdk.common.PyInterpreterItem
import com.intellij.ide.ui.icons.icon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.swing.DefaultListModel
import javax.swing.JList
import javax.swing.JScrollPane
import javax.swing.ListSelectionModel
import javax.swing.event.ListSelectionListener
import kotlinx.coroutines.withContext

internal class PyPackagesSdkController(private val project: Project) : Disposable.Default {

  private val packagingScope: CoroutineScope = PyPackageCoroutine.getScope(project)
    .childScope("Packages SDK Controller", TraceContext(PyBundle.message("trace.context.packages.sdk.controller"), null)).also {
      Disposer.register(this, it.asDisposable())
    }

  private val toolWindowService: PyPackagingToolWindowService
    get() = project.service<PyPackagingToolWindowService>()

  /** Every interpreter a Python project of the structure uses, in the order the list shows them. */
  /** Every interpreter of the Python projects with its list item, sorted by name. Builds each item once. */
  private suspend fun allInterpreters(): List<Pair<PythonInterpreter, PyInterpreterItem>> =
    project.pythonInterpreters().map { it to it.asItem() }.sortedBy { (_, item) -> item.name }

  private val sdkListRenderer = object : SimpleListCellRenderer<PyInterpreterItem>() {
    override fun customize(list: JList<out PyInterpreterItem>, value: PyInterpreterItem, index: Int, selected: Boolean, hasFocus: Boolean) {
      text = value.shortName
      icon = value.icon.icon()
    }
  }

  private val selectionListener = createSelectionListener()

  // Empty until [refreshModuleListAndSelection] fills it: an item states whether its interpreter is usable, which
  // takes running the interpreter, so the list cannot be built on the EDT.
  private val sdkList: JBList<PyInterpreterItem> = JBList<PyInterpreterItem>(DefaultListModel()).apply {
    selectionMode = ListSelectionModel.SINGLE_SELECTION
    cellRenderer = sdkListRenderer
    addListSelectionListener(selectionListener)
  }

  val mainScrollPane: JScrollPane = ScrollPaneFactory.createScrollPane(sdkList, true)

  private val fileEditorListener = object : FileEditorManagerListener {
    override fun selectionChanged(event: FileEditorManagerEvent) {
      val file = event.newFile ?: return
      packagingScope.launch {
        if (project.pythonInterpreters().size <= 1) return@launch
        val interpreter = project.findPythonInterpreter(file) ?: return@launch
        updateSelectedSdkIndex(interpreter)
      }
    }
  }

  init {
    project.messageBus
      .connect(this)
      .subscribe<FileEditorManagerListener>(FILE_EDITOR_MANAGER, fileEditorListener)
  }

  fun refreshModuleListAndSelection() {
    packagingScope.launch {
      val items = loadItems()
      withContext(Dispatchers.EDT) {
        val previous = sdkList.selectedValue
        refreshModuleList(items)
        sdkList.selectedIndex = items.indexOf(previous)
      }
    }
  }

  /** The interpreters of every Python project, as the list holds them. */
  private suspend fun loadItems(): List<PyInterpreterItem> = allInterpreters().map { (_, item) -> item }

  @RequiresEdt(generateAssertion = false /* IJPL-115548 */)
  private fun refreshModuleList(items: List<PyInterpreterItem>) {
    (sdkList.model as DefaultListModel<PyInterpreterItem>).apply {
      removeAllElements()
      addAll(items)
    }
  }

  private fun updateSelectedSdkIndex(interpreter: PythonInterpreter) {
    packagingScope.launch(Dispatchers.EDT) {
      val index = sdkList.indexOfInterpreter(interpreter)
      sdkList.selectionModel.setSelectionInterval(index, index)
    }
  }

  internal fun refreshAndSyncSelection(interpreter: PythonInterpreter?) {
    packagingScope.launch {
      val items = loadItems()
      withContext(Dispatchers.EDT) {
        sdkList.removeListSelectionListener(selectionListener)
        try {
          refreshModuleList(items)
          if (interpreter != null) {
            val index = sdkList.indexOfInterpreter(interpreter)
            if (index >= 0) {
              sdkList.selectionModel.setSelectionInterval(index, index)
            }
          }
        }
        finally {
          sdkList.addListSelectionListener(selectionListener)
        }
      }
    }
  }

  /** Where [interpreter] sits in the list, or -1 when the list does not hold it. Matched by the row's own ref. */
  private fun JBList<PyInterpreterItem>.indexOfInterpreter(interpreter: PythonInterpreter): Int {
    val ref = interpreter.asInterpreterRef()
    return (0 until model.size).firstOrNull { model.getElementAt(it).ref == ref } ?: -1
  }

  private fun createSelectionListener(): ListSelectionListener {
    return ListSelectionListener { event ->
      if (!event.valueIsAdjusting) {
        val selected = sdkList.selectedValue ?: return@ListSelectionListener
        packagingScope.launch {
          val interpreter = allInterpreters().firstOrNull { (_, item) -> item.ref == selected.ref }?.first ?: return@launch
          toolWindowService.initForInterpreter(interpreter)
        }
      }
    }
  }
}