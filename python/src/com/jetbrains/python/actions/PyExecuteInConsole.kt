// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.actions

import com.google.common.collect.Iterables
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.xdebugger.XDebuggerManager
import com.jetbrains.python.console.*
import com.jetbrains.python.run.PythonRunConfiguration

object PyExecuteInConsole {
  @JvmStatic
  fun executeCodeInConsole(project: Project,
                           commandText: String?,
                           editor: Editor?,
                           canUseExistingConsole: Boolean,
                           canUseDebugConsole: Boolean,
                           requestFocusToConsole: Boolean,
                           config: PythonRunConfiguration?) {
    var existingConsole: RunContentDescriptor? = null
    var isDebug = false
    var newConsoleListener: PydevConsoleRunner.ConsoleListener? = null
    if (canUseExistingConsole) {
      if (canUseDebugConsole) {
        existingConsole = getCurrentDebugConsole(project)
      }
      if (existingConsole != null) {
        isDebug = true
      }
      else {
        val virtualFile = (editor as? EditorImpl)?.virtualFile
        if (virtualFile != null && PyExecuteConsoleCustomizer.instance.isCustomDescriptorSupported(virtualFile)) {
          val (descriptor, listener) = getCustomDescriptor(project, editor)
          existingConsole = descriptor
          newConsoleListener = listener
        }
        else {
          existingConsole = getSelectedPythonConsole(project)
        }
      }
    }
    if (existingConsole != null) {
      val console = existingConsole.executionConsole
      (console as PyCodeExecutor).executeCode(commandText, editor)
      showConsoleAndRequestFocus(project, existingConsole, requestFocusToConsole, editor, isDebug)
    }
    else {
      startNewConsoleInstance(project, commandText, config, newConsoleListener)
    }
  }

  private fun getCustomDescriptor(project: Project, editor: Editor?): Pair<RunContentDescriptor?, PydevConsoleRunner.ConsoleListener?> {
    val virtualFile = (editor as? EditorImpl)?.virtualFile ?: return Pair(null, null)
    val executeCustomizer = PyExecuteConsoleCustomizer.instance
    when (executeCustomizer.getCustomDescriptorType(virtualFile)) {
      DescriptorType.NEW -> {
        return Pair(null, createNewConsoleListener(project, executeCustomizer, virtualFile))
      }
      DescriptorType.EXISTING -> {
        val console = executeCustomizer.getExistingDescriptor(virtualFile)
        if (console != null && isAlive(console)) {
          return Pair(console, null)
        }
        else {
          return Pair(null, createNewConsoleListener(project, executeCustomizer, virtualFile))
        }
      }
      else -> {
        throw IllegalStateException("Custom descriptor for ${virtualFile} isn't supported")
      }
    }
  }

  private fun createNewConsoleListener(project: Project, executeCustomizer: PyExecuteConsoleCustomizer,
                                       virtualFile: VirtualFile): PydevConsoleRunner.ConsoleListener {
    return PydevConsoleRunner.ConsoleListener { consoleView ->
      val consoles = getAllRunningConsoles(project)
      val newDescriptor = consoles.find { it.executionConsole === consoleView }
      executeCustomizer.updateDescriptor(virtualFile, DescriptorType.EXISTING, newDescriptor)
    }
  }

  private fun getCurrentDebugConsole(project: Project): RunContentDescriptor? {
    XDebuggerManager.getInstance(project).currentSession?.let { currentSession ->
      val descriptor = currentSession.runContentDescriptor
      if (isAlive(descriptor)) {
        return descriptor
      }
    }
    return null
  }

  fun getAllRunningConsoles(project: Project?): List<RunContentDescriptor> {
    val toolWindow = PythonConsoleToolWindow.getInstance(project!!)
    return if (toolWindow != null && toolWindow.isInitialized) {
      toolWindow.consoleContentDescriptors.filter { isAlive(it) }
    }
    else emptyList()
  }

  private fun getSelectedPythonConsole(project: Project): RunContentDescriptor? {
    val toolWindow = PythonConsoleToolWindow.getInstance(project) ?: return null
    if (!toolWindow.isInitialized) return null
    val consoles = toolWindow.consoleContentDescriptors.filter { isAlive(it) }
    if (consoles.size == 1) {
      return consoles[0]
    }
    else if (consoles.size > 1) {
      val selectedConsole = toolWindow.selectedContentDescriptor
      return if (Iterables.contains(consoles, selectedConsole)) {
        selectedConsole
      }
      else {
        consoles[0]
      }
    }
    return null
  }

  fun isAlive(dom: RunContentDescriptor): Boolean {
    val processHandler = dom.processHandler
    return processHandler != null && !processHandler.isProcessTerminated
  }

  private fun startNewConsoleInstance(project: Project,
                                      runFileText: String?,
                                      config: PythonRunConfiguration?,
                                      listener: PydevConsoleRunner.ConsoleListener?) {
    val consoleRunnerFactory = PythonConsoleRunnerFactory.getInstance()
    val runner = if (runFileText == null || config == null) {
      consoleRunnerFactory.createConsoleRunner(project, null)
    }
    else {
      consoleRunnerFactory.createConsoleRunnerWithFile(project, null, runFileText, config)
    }
    val toolWindow = PythonConsoleToolWindow.getInstance(project)
    runner.addConsoleListener { consoleView ->
      if (consoleView is PyCodeExecutor) {
        (consoleView as PyCodeExecutor).executeCode(runFileText, null)
        toolWindow?.toolWindow?.show(null)
      }
    }
    if (listener != null) {
      runner.addConsoleListener(listener)
    }
    runner.run(false)
  }

  private fun showConsoleAndRequestFocus(project: Project,
                                         descriptor: RunContentDescriptor,
                                         requestFocusToConsole: Boolean,
                                         editor: Editor?,
                                         isDebug: Boolean) {
    if (isDebug) {
      val console = descriptor.executionConsole
      val currentSession = XDebuggerManager.getInstance(project).currentSession
      if (currentSession != null) {
        ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.DEBUG)?.let { debugToolWindow ->
          if (!debugToolWindow.isVisible) {
            debugToolWindow.show()
          }
        }
        // Select "Console" tab in case of Debug console
        val contentManager = currentSession.ui.contentManager
        contentManager.findContent("Console")?.let { content ->
          contentManager.setSelectedContent(content)
        }
        // It's necessary to request focus again after tab selection
        if (requestFocusToConsole) {
          (console as PythonDebugLanguageConsoleView).pydevConsoleView.requestFocus()
        }
        else {
          if (editor != null) {
            IdeFocusManager.findInstance().requestFocus(editor.contentComponent, true)
          }
        }
      }
    }
    else {
      PythonConsoleToolWindow.getInstance(project)?.toolWindow?.let { toolWindow ->
        if (!toolWindow.isVisible) {
          toolWindow.show(null)
        }
        val contentManager = toolWindow.contentManager
        contentManager.findContent(descriptor.displayName)?.let {
          contentManager.setSelectedContent(it)
        }
      }
    }
  }
}