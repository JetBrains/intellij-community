// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.packaging.toolwindow.modules

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.ModuleListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SideBorder
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.util.Function
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.NamedColorUtil
import com.jetbrains.python.icons.PythonIcons
import com.jetbrains.python.packaging.toolwindow.PyPackagingToolWindowService
import com.jetbrains.python.packaging.utils.PyPackageCoroutine
import com.jetbrains.python.sdk.pythonSdk
import kotlinx.coroutines.launch
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.*
import javax.swing.event.ListSelectionListener

class PyPackagesModuleController(val project: Project) : Disposable {
  val packagingScope = PyPackageCoroutine.getIoScope(project)
  val service
    get() = project.service<PyPackagingToolWindowService>()

  val moduleList = JBList(ModuleManager.getInstance(project).modules.toList().sortedBy { it.name }).apply {
    selectionMode = ListSelectionModel.SINGLE_SELECTION
    border = JBUI.Borders.empty()

    val itemRenderer = JBLabel("", PythonIcons.Python.PythonClosed, JLabel.LEFT).apply {
      border = JBUI.Borders.empty(0, 10)
    }
    cellRenderer = ListCellRenderer { _, value, _, _, _ -> itemRenderer.text = value.name; itemRenderer }

    addListSelectionListener(ListSelectionListener { e ->
      if (e.valueIsAdjusting) return@ListSelectionListener
      val selectedModule = this@apply.selectedValue
      val sdk = selectedModule?.pythonSdk ?: return@ListSelectionListener
      packagingScope.launch {
        service.initForSdk(sdk)
      }
    })

    project.messageBus.connect(this@PyPackagesModuleController).subscribe(ModuleListener.TOPIC, object : ModuleListener {
      override fun modulesAdded(project: Project, modules: MutableList<out Module>) = rebuildList()

      override fun moduleRemoved(project: Project, module: Module) = rebuildList()

      override fun modulesRenamed(
        project: Project, modules: MutableList<out Module>,
        oldNameProvider: Function<in Module, String>,
      ) {
        rebuildList()
      }
    })
  }


  private val modulePanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
    border = SideBorder(NamedColorUtil.getBoundsColor(), SideBorder.RIGHT)
    maximumSize = Dimension(80, maximumSize.height)
    minimumSize = Dimension(50, minimumSize.height)
  }.also {
    it.add(moduleList)
  }

  val component = ScrollPaneFactory.createScrollPane(modulePanel, true)

  private val fileListener = object : FileEditorManagerListener {
    override fun selectionChanged(event: FileEditorManagerEvent) {
      if (project.modules.size > 1) {
        val newFile = event.newFile ?: return
        val module = ModuleUtilCore.findModuleForFile(newFile, project)
        packagingScope.launch {
          val index = (moduleList.model as DefaultListModel<Module>).indexOf(module)
          moduleList.selectionModel.setSelectionInterval(index, index)
        }
      }
    }
  }

  init {
    project.messageBus
      .connect(this)
      .subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, fileListener)
  }

  override fun dispose() {}

  fun rebuildList() {
    val selected = moduleList.selectedValue?.name
    val modules: List<Module> = ModuleManager.getInstance(project).modules.toList().sortedBy { it.name }
    val model = moduleList.model as? DefaultListModel
    model?.removeAllElements()
    model?.addAll(modules)
    moduleList.selectedIndex = modules.indexOfFirst { it.name == selected }
  }
}