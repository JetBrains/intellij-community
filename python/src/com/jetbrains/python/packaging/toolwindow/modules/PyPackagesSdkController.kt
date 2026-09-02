// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.toolwindow.modules

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.FileEditorManagerListener.FILE_EDITOR_MANAGER
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.util.coroutines.childScope
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBList
import com.intellij.util.asDisposable
import com.jetbrains.python.PyBundle
import com.jetbrains.python.TraceContext
import com.jetbrains.python.packaging.toolwindow.PyPackagingToolWindowService
import com.jetbrains.python.packaging.utils.PyPackageCoroutine
import com.intellij.python.sdk.backend.asInterpreterRef
import com.intellij.python.sdk.backend.findSdk
import com.intellij.python.sdk.backend.pyInterpreterItems
import com.intellij.python.sdk.common.PyInterpreterItem
import com.intellij.ide.ui.icons.icon
import com.intellij.openapi.application.readAction
import com.jetbrains.python.sdk.pythonSdk
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

  private val allSdks: List<Sdk>
    get() = ModuleManager.getInstance(project).modules.mapNotNull { it.pythonSdk }.distinct().sortedBy { it.name }

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
      if (allSdks.size <= 1) return
      val sdk = getModuleForVirtualFile(event.newFile)?.pythonSdk ?: return
      updateSelectedSdkIndex(sdk)
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

  /** The interpreters of every module, as the list holds them. Runs each interpreter, so never on the EDT. */
  private suspend fun loadItems(): List<PyInterpreterItem> = readAction { allSdks }.pyInterpreterItems()

  @RequiresEdt
  private fun refreshModuleList(items: List<PyInterpreterItem>) {
    (sdkList.model as DefaultListModel<PyInterpreterItem>).apply {
      removeAllElements()
      addAll(items)
    }
  }

  private fun updateSelectedSdkIndex(sdk: Sdk) {
    packagingScope.launch(Dispatchers.EDT) {
      val index = sdkList.indexOfInterpreter(sdk)
      sdkList.selectionModel.setSelectionInterval(index, index)
    }
  }

  internal fun refreshAndSyncSelection(sdk: Sdk?) {
    packagingScope.launch {
      val items = loadItems()
      withContext(Dispatchers.EDT) {
        sdkList.removeListSelectionListener(selectionListener)
        try {
          refreshModuleList(items)
          if (sdk != null) {
            val index = sdkList.indexOfInterpreter(sdk)
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

  /** Where [sdk] sits in the list, or -1 when the list does not hold it. Matched by the row's own ref. */
  private fun JBList<PyInterpreterItem>.indexOfInterpreter(sdk: Sdk): Int {
    val ref = sdk.asInterpreterRef()
    return (0 until model.size).firstOrNull { model.getElementAt(it).ref == ref } ?: -1
  }

  private fun createSelectionListener(): ListSelectionListener {
    return ListSelectionListener { event ->
      if (!event.valueIsAdjusting) {
        val selected = sdkList.selectedValue ?: return@ListSelectionListener
        packagingScope.launch {
          val selectedSdk = readAction { selected.findSdk() } ?: return@launch
          toolWindowService.initForSdk(selectedSdk)
        }
      }
    }
  }

  private fun getModuleForVirtualFile(file: VirtualFile?): Module? {
    file ?: return null
    return ModuleUtilCore.findModuleForFile(file, project)
  }
}