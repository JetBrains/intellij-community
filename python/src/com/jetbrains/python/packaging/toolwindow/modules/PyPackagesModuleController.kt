// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.toolwindow.modules

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBList
import com.jetbrains.python.icons.PythonIcons
import com.jetbrains.python.packaging.toolwindow.PyPackagingToolWindowService
import com.jetbrains.python.packaging.utils.PyPackageCoroutine
import com.jetbrains.python.sdk.pythonSdk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.swing.DefaultListModel
import javax.swing.JList
import javax.swing.JScrollPane
import javax.swing.ListSelectionModel
import javax.swing.event.ListSelectionListener

internal class PyPackagesModuleController(private val project: Project) : Disposable {

  private val packagingScope: CoroutineScope = PyPackageCoroutine.getIoScope(project)

  private val toolWindowService: PyPackagingToolWindowService
    get() = project.service<PyPackagingToolWindowService>()

  private val allModules: List<Module>
    get() = ModuleManager.getInstance(project).modules.toList().sortedBy { it.name }

  private val moduleListRenderer = object : SimpleListCellRenderer<Module?>() {
    override fun customize(list: JList<out Module?>, value: Module?, index: Int, selected: Boolean, hasFocus: Boolean) {
      text = value?.name ?: ""
      icon = PythonIcons.Python.PythonClosed
    }
  }

  private val moduleList: JBList<Module> = JBList(allModules).apply {
    selectionMode = ListSelectionModel.SINGLE_SELECTION
    cellRenderer = moduleListRenderer
    addListSelectionListener(createSelectionListener())
  }

  val mainScrollPane: JScrollPane = ScrollPaneFactory.createScrollPane(moduleList, true)

  private val fileEditorListener = object : FileEditorManagerListener {
    override fun selectionChanged(event: FileEditorManagerEvent) {
      if (allModules.size > 1) {
        val module = getModuleForVirtualFile(event.newFile) ?: return
        updateSelectedModuleIndex(module)
      }
    }
  }

  init {
    subscribeToFileEditorEvents()
  }

  fun refreshModuleListAndSelection() {
    refreshModuleList()
    restorePreviousSelection()
  }

  private fun refreshModuleList() {
    (moduleList.model as DefaultListModel<Module>).apply {
      removeAllElements()
      addAll(allModules)
    }
  }

  private fun restorePreviousSelection() {
    val selectedModuleName = moduleList.selectedValue?.name
    moduleList.selectedIndex = allModules.indexOfFirst { it.name == selectedModuleName }
  }

  private fun updateSelectedModuleIndex(module: Module) {
    packagingScope.launch(Dispatchers.EDT) {
      val index = (moduleList.model as DefaultListModel<Module>).indexOf(module)
      moduleList.selectionModel.setSelectionInterval(index, index)
    }
  }

  private fun createSelectionListener(): ListSelectionListener {
    return ListSelectionListener { event ->
      if (!event.valueIsAdjusting) {
        val selectedSdk = moduleList.selectedValue?.pythonSdk ?: return@ListSelectionListener
        packagingScope.launch { toolWindowService.initForSdk(selectedSdk) }
      }
    }
  }

  private fun subscribeToFileEditorEvents() {
    project.messageBus
      .connect(this)
      .subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, fileEditorListener)
  }

  private fun getModuleForVirtualFile(file: VirtualFile?): Module? {
    return file?.let { ModuleUtilCore.findModuleForFile(it, project) }
  }

  override fun dispose() { }
}