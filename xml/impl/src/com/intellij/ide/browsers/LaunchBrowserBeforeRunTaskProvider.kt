/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.ide.browsers

import com.intellij.execution.BeforeRunTask
import com.intellij.execution.BeforeRunTaskProvider
import com.intellij.execution.ExecutionListener
import com.intellij.execution.ExecutionManager
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.ui.components.CheckBox
import com.intellij.ui.components.dialog
import com.intellij.ui.layout.*
import com.intellij.util.ui.UIUtil
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.xml.XmlBundle
import javax.swing.Icon
import javax.swing.border.EmptyBorder

internal class LaunchBrowserBeforeRunTaskProvider : BeforeRunTaskProvider<LaunchBrowserBeforeRunTask>() {
  companion object {
    val ID = Key.create<LaunchBrowserBeforeRunTask>("LaunchBrowser.Before.Run")
  }

  override fun getName() = "Launch Web Browser"

  override fun getId() = ID

  override fun getIcon(): Icon = AllIcons.Nodes.PpWeb

  override fun isConfigurable() = true

  override fun createTask(runConfiguration: RunConfiguration) = LaunchBrowserBeforeRunTask()

  override fun configureTask(runConfiguration: RunConfiguration, task: LaunchBrowserBeforeRunTask): Boolean {
    val state = task.state
    val modificationCount = state.modificationCount

    val browserSelector = BrowserSelector()
    val browserComboBox = browserSelector.mainComponent
    if (UIUtil.isUnderAquaLookAndFeel()) {
      browserComboBox.border = EmptyBorder(3, 0, 0, 0)
    }
    state.browser?.let {
      browserSelector.selected = it
    }

    val url = TextFieldWithBrowseButton()
    state.url?.let {
      url.text = it
    }

    StartBrowserPanel.setupUrlField(url, runConfiguration.project)

    val startJavaScriptDebuggerCheckBox = if (JavaScriptDebuggerStarter.Util.hasStarters()) CheckBox(XmlBundle.message("start.browser.with.js.debugger"), state.withDebugger) else null

    val panel = panel {
      row("Browser:") {
        browserComboBox()
        startJavaScriptDebuggerCheckBox?.invoke()
      }
      row("Url:") {
        url(growPolicy = GrowPolicy.MEDIUM_TEXT)
      }
    }
    dialog("Launch Web Browser", panel = panel, resizable = true, focusedComponent = url)
      .show()

    state.browser = browserSelector.selected
    state.url = url.text
    if (startJavaScriptDebuggerCheckBox != null) {
      state.withDebugger = startJavaScriptDebuggerCheckBox.isSelected
    }
    return modificationCount != state.modificationCount
  }

  override fun executeTask(context: DataContext?, configuration: RunConfiguration, env: ExecutionEnvironment, task: LaunchBrowserBeforeRunTask): Boolean {
    val disposable = Disposer.newDisposable()
    Disposer.register(env.project, disposable)
    val executionId = env.executionId
    env.project.messageBus.connect(disposable).subscribe(ExecutionManager.EXECUTION_TOPIC, object: ExecutionListener {
      override fun processNotStarted(executorId: String, env: ExecutionEnvironment) {
        Disposer.dispose(disposable)
      }

      override fun processStarted(executorId: String, env: ExecutionEnvironment, handler: ProcessHandler) {
        if (env.executionId != executionId) {
          return
        }

        Disposer.dispose(disposable)

        val settings = StartBrowserSettings()
        settings.browser = task.state.browser
        settings.isStartJavaScriptDebugger = task.state.withDebugger
        settings.url = task.state.url
        settings.isSelected = true
        BrowserStarter(configuration, settings, handler).start()
      }
    })
    return true
  }
}

internal class LaunchBrowserBeforeRunTaskState : BaseState() {
  @get:Attribute(value = "browser", converter = WebBrowserReferenceConverter::class)
  var browser by property<WebBrowser>()
  @get:Attribute()
  var url by string()
  @get:Attribute()
  var withDebugger by property(false)
}

internal class LaunchBrowserBeforeRunTask : BeforeRunTask<LaunchBrowserBeforeRunTask>(LaunchBrowserBeforeRunTaskProvider.ID), PersistentStateComponent<LaunchBrowserBeforeRunTaskState> {
  private var state = LaunchBrowserBeforeRunTaskState()

  override fun loadState(state: LaunchBrowserBeforeRunTaskState) {
    state.resetModificationCount()
    this.state = state
  }

  override fun getState() = state
}