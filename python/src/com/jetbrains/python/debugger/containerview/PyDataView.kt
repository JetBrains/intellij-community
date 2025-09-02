// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger.containerview

import com.intellij.execution.process.ProcessHandler
import com.intellij.icons.AllIcons
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ToolWindowType
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.content.ContentManager
import com.intellij.ui.dsl.builder.panel
import com.jetbrains.python.PyBundle
import com.jetbrains.python.console.PydevConsoleCommunication
import com.jetbrains.python.debugger.PyDebugProcess
import com.jetbrains.python.debugger.PyDebugValue
import com.jetbrains.python.debugger.PyFrameAccessor
import com.jetbrains.python.icons.PythonIcons
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Predicate
import javax.swing.JComponent

@Service(Service.Level.PROJECT)
class PyDataView(private val project: Project) : DumbAware {
  private val selectedInfos: MutableMap<ProcessHandler, Content> = ConcurrentHashMap()

  private lateinit var contentManager: ContentManager

  fun init(toolWindow: ToolWindow) {
    toolWindow.stripeTitle = PyBundle.message("debugger.data.view.title")
    toolWindow.helpId = HELP_ID
    toolWindow.isAvailable = true

    contentManager = toolWindow.contentManager

    addEmptyContent()

    project.messageBus.connect()
      .subscribe<ToolWindowManagerListener>(
        ToolWindowManagerListener.TOPIC,
        object : ToolWindowManagerListener {
          override fun stateChanged(toolWindowManager: ToolWindowManager) {
            val window = toolWindowManager.getToolWindow(DATA_VIEWER_ID)
            if (window == null) {
              return
            }
            if (toolWindow.isAvailable && toolWindow.type == ToolWindowType.FLOATING && !toolWindow.isVisible) {
              toolWindow.isShowStripeButton = false
              toolWindow.contentManager.removeAllContents(true)
            }
          }
        })
  }

  private fun addEmptyContent() {
    val content = ContentFactory.getInstance().createContent(createEmptyContent(), null, false)
    content.isCloseable = false
    contentManager.addContent(content)
  }

  fun show(value: PyDebugValue) {
    if (ToolWindowManager.getInstance(project).getToolWindow(DATA_VIEWER_ID) != null) {
      showInToolWindow(value)
    }
    else {
      ApplicationManager.getApplication().invokeLater {
        PyDataViewDialog(project, value).show()
      }
    }
  }

  private fun showInToolWindow(value: PyDebugValue) {
    val window = ToolWindowManager.getInstance(project).getToolWindow(DATA_VIEWER_ID)
    if (window == null) {
      thisLogger().error("Tool window '$DATA_VIEWER_ID' is not found")
      return
    }
    window.contentManager.getReady(this).doWhenDone {
      val selectedInfo = addTab(value.frameAccessor)
      val dataViewerPanel = selectedInfo.component as PyDataViewerPanel
      dataViewerPanel.component.apply(value, false)
      window.show {
        window.component.requestFocusInWindow()
        dataViewerPanel.requestFocusInWindow()
      }
    }
  }

  fun closeTabs(ifClose: Predicate<PyFrameAccessor>) {
    val tabsToRemove: MutableList<Content> = ArrayList()

    contentManager.contents.forEach {
      if (ifClose.test(getPanel(it.component).component.dataViewerModel.frameAccessor)) {
        tabsToRemove.add(it)
      }
    }

    ApplicationManager.getApplication().invokeLater {
      tabsToRemove.forEach {
        contentManager.removeContent(it, true)
      }
    }
  }

  fun updateTabs(handler: ProcessHandler) {
    saveSelectedInfo()
    contentManager.contents.forEach { content ->
      val panel: PyDataViewerAbstractPanel = getPanel(content.component).component
      val accessor = panel.dataViewerModel.frameAccessor
      if (accessor !is PyDebugProcess) {
        return@forEach
      }

      val shouldBeShown = Comparing.equal(handler, accessor.processHandler)
      if (!shouldBeShown) {
        contentManager.removeContent(content, true)
      }
    }

    restoreSelectedInfo(handler)
  }

  private fun restoreSelectedInfo(handler: ProcessHandler) {
    val savedSelection = selectedInfos[handler]
    if (savedSelection != null) {
      contentManager.setSelectedContent(savedSelection)
      selectedInfos.remove(handler)
    }
  }

  private fun saveSelectedInfo() {
    val selectedInfo = contentManager.selectedContent
    if (!hasOnlyEmptyTab() && selectedInfo != null) {
      val accessor: PyFrameAccessor = getPanel(selectedInfo.component).component.dataViewerModel.frameAccessor
      if (accessor is PyDebugProcess) {
        selectedInfos[accessor.processHandler] = selectedInfo
      }
    }
  }

  fun closeDisconnectedFromConsoleTabs() {
    closeTabs { frameAccessor: PyFrameAccessor? ->
      frameAccessor is PydevConsoleCommunication && !isConnected(frameAccessor)
    }
  }

  fun isConnected(accessor: PydevConsoleCommunication): Boolean {
    return !accessor.isCommunicationClosed
  }

  private fun createEmptyContent() = panel {
    row {
      panel { row { text(PyBundle.message("debugger.data.view.empty.text")) } }
    }.resizableRow()
  }

  fun addTab(frameAccessor: PyFrameAccessor): Content {
    if (hasOnlyEmptyTab()) {
      contentManager.removeAllContents(true)
    }

    val panel = PyDataViewerPanel(project, frameAccessor)

    val content = ContentFactory.getInstance().createContent(panel, null, false)
    content.isCloseable = true
    content.displayName = PyBundle.message("debugger.data.view.empty.tab")
    if (frameAccessor is PydevConsoleCommunication) {
      content.icon = PythonIcons.Python.PythonConsole
      content.description = PyBundle.message("debugger.data.view.connected.to.python.console")
    }
    else if (frameAccessor is PyDebugProcess) {
      content.icon = AllIcons.Toolwindows.ToolWindowDebugger
      content.description = PyBundle.message("debugger.data.view.connected.to.debug.session", frameAccessor.session.sessionName)
    }

    val window: ToolWindow? = ToolWindowManager.getInstance(project).getToolWindow(DATA_VIEWER_ID)
    if (window is ToolWindowEx) {
      window.setTabActions(NewViewerAction(frameAccessor))
    }
    panel.addListener( PyDataViewerAbstractPanel.OnNameChangedListener {
      content.displayName = it
    })
    Disposer.register(content, panel)

    contentManager.addContent(content)
    contentManager.setSelectedContent(content)

    return content
  }

  private fun hasOnlyEmptyTab(): Boolean {
    return contentManager.contentCount == 1 && contentManager.contents.first().component !is PyDataViewerPanel
  }

  fun getVisibleTabs(): List<Content> {
    return contentManager.contents.toList() //myTabs.tabs.filter { tabInfo: TabInfo -> !tabInfo.isHidden }
  }

  inner class NewViewerAction(private val myFrameAccessor: PyFrameAccessor) : DumbAwareAction(
    PyBundle.message("debugger.data.view.view.new.container"),
    PyBundle.message(
      "debugger.data.view.open.new.container.viewer"),
    AllIcons.General.Add) {
    override fun actionPerformed(e: AnActionEvent) {
      addTab(myFrameAccessor)
    }
  }

  fun changeAutoResize(autoResize: Boolean) {
    contentManager.contents.forEach {
      (getPanel(it.component).component as PyDataViewerCommunityPanel).resize(autoResize)
    }
  }

  fun getPanel(component: JComponent): PyDataViewerPanel {
    return component as PyDataViewerPanel
  }

  companion object {
    private const val DATA_VIEWER_ID = "SciView"

    const val COLORED_BY_DEFAULT: String = "datagrid.heatmap.switchedByDefault"
    const val AUTO_RESIZE: String = "python.debugger.dataView.autoresize"
    private const val HELP_ID = "reference.toolWindows.PyDataView"

    @JvmStatic
    fun isAutoResizeEnabled(project: Project): Boolean = PropertiesComponent.getInstance(project).getBoolean(AUTO_RESIZE, true)

    @JvmStatic
    fun isColoringEnabled(project: Project): Boolean = PropertiesComponent.getInstance(project).getBoolean(COLORED_BY_DEFAULT, true)

    @JvmStatic
    fun setColoringEnabled(project: Project, value: Boolean) {
      PropertiesComponent.getInstance(project).setValue(COLORED_BY_DEFAULT, value, true)
    }

    @JvmStatic
    fun setAutoResizeEnabled(project: Project, value: Boolean) {
      PropertiesComponent.getInstance(project).setValue(AUTO_RESIZE, value, true)
    }

    fun getInstance(project: Project): PyDataView = project.service<PyDataView>()
  }
}