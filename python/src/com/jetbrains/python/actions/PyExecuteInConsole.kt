// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("PyExecuteInConsole")

package com.jetbrains.python.actions

import com.intellij.codeInsight.hint.HintManager
import com.intellij.execution.target.value.TargetEnvironmentFunction
import com.intellij.execution.ui.ExecutionConsole
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.ContentManager
import com.intellij.xdebugger.XDebuggerManager
import com.jetbrains.python.PyBundle
import com.jetbrains.python.console.*
import com.jetbrains.python.run.PythonRunConfiguration
import java.util.function.Consumer
import java.util.function.Function

fun executeCodeInConsole(project: Project,
                         commandText: List<String>,
                         editor: Editor?,
                         canUseExistingConsole: Boolean,
                         canUseDebugConsole: Boolean,
                         requestFocusToConsole: Boolean,
                         config: PythonRunConfiguration?) {
  val executeCodeInConsole = commandText.foldRight(null) { commandText: String, acc: Consumer<ExecutionConsole>? ->
    var consumer = Consumer<ExecutionConsole> { (it as PyCodeExecutor).executeCode(commandText, editor) }
    if (acc != null) {
      consumer = consumer.andThen(acc)
    }
    consumer
  }
  val executeInStartingConsole = Function<VirtualFile?, Boolean> { PyExecuteConsoleCustomizer.instance.isConsoleStarting(it, null) }
  executeCodeInConsole(project, executeCodeInConsole, executeInStartingConsole, editor, canUseExistingConsole, canUseDebugConsole,
                       requestFocusToConsole, config)
}

fun executeCodeInConsole(project: Project,
                         commandText: String?,
                         editor: Editor?,
                         canUseExistingConsole: Boolean,
                         canUseDebugConsole: Boolean,
                         requestFocusToConsole: Boolean,
                         config: PythonRunConfiguration?) {
  val executeCodeInConsole = commandText?.let { Consumer<ExecutionConsole> { (it as PyCodeExecutor).executeCode(commandText, editor) } }
  val executeInStartingConsole = Function<VirtualFile?, Boolean> { PyExecuteConsoleCustomizer.instance.isConsoleStarting(it, commandText) }
  executeCodeInConsole(project, executeCodeInConsole, executeInStartingConsole, editor, canUseExistingConsole, canUseDebugConsole,
                       requestFocusToConsole, config)
}

fun executeCodeInConsole(project: Project,
                         commandText: TargetEnvironmentFunction<String>,
                         editor: Editor?,
                         canUseExistingConsole: Boolean,
                         canUseDebugConsole: Boolean,
                         requestFocusToConsole: Boolean,
                         config: PythonRunConfiguration?) {
  val executeCodeInConsole = Consumer<ExecutionConsole> { (it as PyTargetedCodeExecutor).executeCode(commandText) }
  val executeInStartingConsole = Function<VirtualFile?, Boolean> { PyExecuteConsoleCustomizer.instance.isConsoleStarting(it, null) }
  executeCodeInConsole(project, executeCodeInConsole, executeInStartingConsole, editor, canUseExistingConsole, canUseDebugConsole,
                       requestFocusToConsole, config)
}

private fun executeCodeInConsole(project: Project,
                                 executeInConsole: Consumer<ExecutionConsole>?,
                                 executeInStartingConsole: Function<VirtualFile?, Boolean>,
                                 editor: Editor?,
                                 canUseExistingConsole: Boolean,
                                 canUseDebugConsole: Boolean,
                                 requestFocusToConsole: Boolean,
                                 config: PythonRunConfiguration?) {
  var existingConsole: RunContentDescriptor? = null
  var isDebug = false
  var newConsoleListener: PydevConsoleRunner.ConsoleListener? = null
  val virtualFile = (editor as? EditorImpl)?.virtualFile
  if (!checkIfAvailableAndShowHint(editor)) return
  if (canUseExistingConsole) {
    if (virtualFile != null && PyExecuteConsoleCustomizer.instance.isCustomDescriptorSupported(virtualFile)) {
      val (descriptor, listener) = getCustomDescriptor(project, virtualFile)
      existingConsole = descriptor
      newConsoleListener = listener
    }
    else {
      existingConsole = getSelectedPythonConsole(project)
    }
    if (canUseDebugConsole) {
      if (PythonConsoleToolWindow.getInstance(project)?.toolWindow?.isVisible != true) {
        // PY-48207 Currently visible Python Console has a higher priority than a Debug console
        val debugConsole = getCurrentDebugConsole(project)
        if (debugConsole != null) {
          existingConsole = debugConsole
          isDebug = true
        }
      }
    }
  }
  if (existingConsole != null) {
    val console = existingConsole.executionConsole
    executeInConsole?.accept(console)
    val consoleView = showConsole(project, existingConsole, isDebug)
    requestFocus(requestFocusToConsole, editor, consoleView, isDebug)
  }
  else {
    if (!executeInStartingConsole.apply(virtualFile)) {
      startNewConsoleInstance(project, virtualFile, executeInConsole, config, newConsoleListener, isRequestFocus = requestFocusToConsole)
    }
  }
}

fun checkIfAvailableAndShowHint(editor: Editor?): Boolean {
  val virtualFile = (editor as? EditorImpl)?.virtualFile
  if (editor != null && virtualFile != null && PyExecuteConsoleCustomizer.instance.getCustomDescriptorType(virtualFile) ==
      DescriptorType.NON_INTERACTIVE) {
    HintManager.getInstance().showErrorHint(editor, PyBundle.message("python.console.toolbar.action.available.non.interactive"))
    return false
  }
  return true
}

fun getCustomDescriptor(project: Project, virtualFile: VirtualFile): Pair<RunContentDescriptor?, PydevConsoleRunner.ConsoleListener?> {
  val executeCustomizer = PyExecuteConsoleCustomizer.instance
  when (executeCustomizer.getCustomDescriptorType(virtualFile)) {
    DescriptorType.NEW -> {
      return Pair(null, createNewConsoleListener(project, virtualFile))
    }
    DescriptorType.EXISTING -> {
      val console = executeCustomizer.getExistingDescriptor(virtualFile)
      if (console != null && isAlive(console)) {
        return Pair(console, null)
      }
      else {
        return Pair(null, createNewConsoleListener(project, virtualFile))
      }
    }
    DescriptorType.STARTING -> {
      return Pair(null, null)
    }
    DescriptorType.NON_INTERACTIVE -> {
      throw IllegalStateException("This code shouldn't be called for a non-interactive descriptor")
    }
    else -> {
      throw IllegalStateException("Custom descriptor for ${virtualFile} is null")
    }
  }
}

fun createNewConsoleListener(project: Project, virtualFile: VirtualFile): PydevConsoleRunner.ConsoleListener {
  return PydevConsoleRunner.ConsoleListener { consoleView ->
    val consoles = getAllRunningConsoles(project)
    val newDescriptor = consoles.find { it.executionConsole === consoleView }
    PyExecuteConsoleCustomizer.instance.updateDescriptor(virtualFile, DescriptorType.EXISTING, newDescriptor)
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

fun getSelectedPythonConsole(project: Project): RunContentDescriptor? {
  val toolWindow = PythonConsoleToolWindow.getInstance(project) ?: return null
  if (!toolWindow.isInitialized) return null
  val consoles = toolWindow.consoleContentDescriptors.filter { isAlive(it) }
  return consoles.singleOrNull()
         ?: toolWindow.selectedContentDescriptor.takeIf { it in consoles }
         ?: consoles.firstOrNull()
}

fun isAlive(dom: RunContentDescriptor): Boolean {
  val processHandler = dom.processHandler
  return processHandler != null && !processHandler.isProcessTerminated
}

private fun startNewConsoleInstance(project: Project,
                                    virtualFile: VirtualFile?,
                                    executeInConsole: Consumer<ExecutionConsole>?,
                                    config: PythonRunConfiguration?,
                                    listener: PydevConsoleRunner.ConsoleListener?,
                                    isRequestFocus: Boolean = false) {
  val consoleRunnerFactory = PythonConsoleRunnerFactory.getInstance()
  val runner: PydevConsoleRunner = ApplicationManager.getApplication().runReadAction<PydevConsoleRunner> {
    if (executeInConsole == null || config == null) {
      consoleRunnerFactory.createConsoleRunner(project, null)
    }
    else {
      consoleRunnerFactory.createConsoleRunnerWithFile(project, config)
    }
  }
  runner.addConsoleListener { consoleView ->
    if (consoleView is PyCodeExecutor) {
      executeInConsole?.accept(consoleView)
      PythonConsoleToolWindow.getInstance(project)?.toolWindow?.show(null)
    }
  }
  if (listener != null) {
    runner.addConsoleListener(listener)
  }
  virtualFile?.let {
    PyExecuteConsoleCustomizer.instance.notifyRunnerStart(it, runner)
  }
  runner.run(isRequestFocus)
}

private fun showConsole(project: Project,
                        descriptor: RunContentDescriptor,
                        isDebug: Boolean): PythonConsoleView? {
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
      val sessionUi = currentSession.ui
      if (sessionUi != null) {
        selectConsoleTab(descriptor, sessionUi.contentManager, isDebug = true)
      }
      else {
        // TODO [Debugger.RunnerLayoutUi]
      }
      return (console as PythonDebugLanguageConsoleView).pydevConsoleView
    }
  }
  else {
    PythonConsoleToolWindow.getInstance(project)?.toolWindow?.let { toolWindow ->
      if (!toolWindow.isVisible) {
        ApplicationManager.getApplication().invokeLater { toolWindow.show(null) }
      }
      selectConsoleTab(descriptor, toolWindow.contentManager, isDebug=false)
    }
    return descriptor.executionConsole as? PythonConsoleView
  }
  return null
}

fun selectConsoleTab(descriptor: RunContentDescriptor, contentManager: ContentManager, isDebug: Boolean) {
  val tabName = if (isDebug) "Console" else PyExecuteConsoleCustomizer.instance.getDescriptorName(descriptor)
  contentManager.findContent(tabName)?.let { content -> contentManager.setSelectedContent(content) }
}


fun requestFocus(requestFocusToConsole: Boolean, editor: Editor?, consoleView: PythonConsoleView?, isDebug: Boolean) {
  if (requestFocusToConsole) {
    consoleView?.let {
      if (isDebug) {
        IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown {
          IdeFocusManager.getGlobalInstance().requestFocus(it.consoleEditor.contentComponent, true)
        }
      }
      else {
        it.requestFocus()
      }
    }
  }
  else {
    if (editor != null) {
      IdeFocusManager.findInstance().requestFocus(editor.contentComponent, true)
    }
  }
}