// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.toolwindow.modules

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.FileEditorManagerListener.FILE_EDITOR_MANAGER
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.JBList
import com.jetbrains.python.packaging.toolwindow.PyPackagingToolWindowService
import com.jetbrains.python.packaging.utils.PyPackageCoroutine
import com.jetbrains.python.sdk.PySdkPopupFactory
import com.jetbrains.python.sdk.icon
import com.jetbrains.python.sdk.pythonSdk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.swing.DefaultListModel
import javax.swing.JList
import javax.swing.JScrollPane
import javax.swing.ListSelectionModel
import javax.swing.event.ListSelectionListener

internal class PyPackagesSdkController(private val project: Project) : Disposable.Default {

  private val packagingScope: CoroutineScope = PyPackageCoroutine.getIoScope(project)

  private val toolWindowService: PyPackagingToolWindowService
    get() = project.service<PyPackagingToolWindowService>()

  private val allSdks: List<Sdk>
    get() = ModuleManager.getInstance(project).modules.mapNotNull { it.pythonSdk }.distinct().sortedBy { it.name }

  private val sdkListRenderer = object : SimpleListCellRenderer<Sdk>() {
    override fun customize(list: JList<out Sdk>, value: Sdk, index: Int, selected: Boolean, hasFocus: Boolean) {
      text = PySdkPopupFactory.shortenNameInPopup(value, 50)
      icon = icon(value)
    }
  }

  private val sdkList: JBList<Sdk> = JBList(allSdks).apply {
    selectionMode = ListSelectionModel.SINGLE_SELECTION
    cellRenderer = sdkListRenderer
    addListSelectionListener(createSelectionListener())
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
    refreshModuleList()
    restorePreviousSelection()
  }

  private fun refreshModuleList() {
    (sdkList.model as DefaultListModel<Sdk>).apply {
      removeAllElements()
      addAll(allSdks)
    }
  }

  private fun restorePreviousSelection() {
    val selectedModuleName = sdkList.selectedValue?.name
    sdkList.selectedIndex = allSdks.indexOfFirst { it.name == selectedModuleName }
  }

  private fun updateSelectedSdkIndex(sdk: Sdk) {
    packagingScope.launch(Dispatchers.EDT) {
      val index = (sdkList.model as DefaultListModel<Sdk>).indexOf(sdk)
      sdkList.selectionModel.setSelectionInterval(index, index)
    }
  }

  private fun createSelectionListener(): ListSelectionListener {
    return ListSelectionListener { event ->
      if (!event.valueIsAdjusting) {
        val selectedSdk = sdkList.selectedValue ?: return@ListSelectionListener
        packagingScope.launch { toolWindowService.initForSdk(selectedSdk) }
      }
    }
  }

  private fun getModuleForVirtualFile(file: VirtualFile?): Module? {
    file ?: return null
    return ModuleUtilCore.findModuleForFile(file, project)
  }
}