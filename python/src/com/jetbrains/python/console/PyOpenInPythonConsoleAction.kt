// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.console

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.jetbrains.python.PyBundle
import com.jetbrains.python.icons.PythonIcons

/**
 * "Open In | Python Console" on a subproject: starts a console on that subproject's interpreter, with the
 * subproject's own root on `sys.path`.
 *
 * The target and its interpreter both come from [resolveConsoleTarget], which answers the way the interpreter widget
 * does, so the console and the status bar never disagree about a directory. A uv or poetry workspace needs no special
 * case: a member's interpreter is already the one declared at the workspace root, while the root that reaches
 * `sys.path` stays the member's own.
 *
 * That root reaches `sys.path` as the console's working directory: it comes from the module's first content root, and
 * `constructPyPathAndWorkingDirCommand` always puts the working directory on the path. The "Add content roots to
 * PYTHONPATH" setting is therefore not what carries it. A working directory pinned in the Python Console settings
 * does win, for this console as for every other one in the project.
 */
internal class PyOpenInPythonConsoleAction : AnAction(), DumbAware {

  init {
    templatePresentation.icon = PythonIcons.Python.PythonConsole
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val presentation = e.presentation
    presentation.isEnabledAndVisible = false
    val project = e.project ?: return
    val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
    // Blocking belongs here and only here: getActionUpdateThread names BGT, so this is a background thread with no
    // write action, which is the state runBlockingCancellable asks for.
    val target = runBlockingCancellable { resolveConsoleTarget(project, file) } ?: return
    presentation.isVisible = true
    // The one case the issue asks to grey out rather than hide: a Python subproject with no interpreter set up yet.
    setConsoleInterpreterState(
      presentation = presentation,
      hasInterpreter = target.interpreter != null,
      reason = PyBundle.message("python.console.no.interpreter.subproject"),
      defaultDescription = templatePresentation.description,
    )
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
    // A modal progress, not runBlockingCancellable: this runs on the EDT, where that call is forbidden because it
    // does not pump the event queue. Once a project has opened the lookup is a cached one, so no dialog appears.
    val target = runWithModalProgressBlocking(project, PyBundle.message("progress.title.starting.python.console")) {
      resolveConsoleTarget(project, file)
    } ?: return

    if (focusExistingConsole(project, consoleTabTitle(project, target.module, PyConsoleType.PYTHON.title))) return

    launchPythonConsoleRunner(project, target.module) { runner ->
      runner.addConsoleListener { PythonConsoleToolWindow.getInstance(project)?.toolWindow?.show(null) }
      runner.run(true)
    }
  }

  /**
   * Whether a console for this subproject is already open, in which case it is brought to the front instead of a
   * second one being started. Without this the runner would number the second tab, and one subproject would answer
   * from two consoles.
   */
  private fun focusExistingConsole(project: Project, title: String): Boolean {
    val toolWindow = PythonConsoleToolWindow.getInstance(project)?.takeIf { it.isInitialized } ?: return false
    val contentManager = toolWindow.toolWindow.contentManager
    val existing = contentManager.contents.firstOrNull { it.displayName == title } ?: return false
    toolWindow.toolWindow.activate { contentManager.setSelectedContent(existing) }
    return true
  }

}
