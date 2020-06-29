// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.newProject.welcome

import com.intellij.execution.RunManager
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.RunConfigurationProducer
import com.intellij.ide.IdeBundle
import com.intellij.ide.projectView.ProjectView
import com.intellij.ide.projectView.impl.ProjectViewPane
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.AppUIExecutor
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.keymap.KeymapUtil.getShortcutText
import com.intellij.openapi.keymap.MacKeymapUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.DumbAwareRunnable
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectCoreUtil
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.ui.CheckBoxWithDescription
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.platform.DirectoryProjectConfigurator
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.ui.components.JBCheckBox
import com.intellij.util.ui.tree.TreeUtil
import com.intellij.xdebugger.XDebuggerUtil
import com.jetbrains.python.newProject.welcome.PyWelcomeCollector.Companion.ProjectType
import com.jetbrains.python.newProject.welcome.PyWelcomeCollector.Companion.ProjectViewResult
import com.jetbrains.python.newProject.welcome.PyWelcomeCollector.Companion.ScriptResult
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.run.PythonRunConfigurationProducer
import com.jetbrains.python.sdk.pythonSdk
import org.jetbrains.annotations.CalledInAny
import org.jetbrains.concurrency.CancellablePromise
import java.awt.event.ItemEvent
import java.util.concurrent.Callable
import javax.swing.JPanel

internal class PyWelcomeConfigurator : DirectoryProjectConfigurator {
  override fun isEdtRequired() = false

  override fun configureProject(project: Project, baseDir: VirtualFile, moduleRef: Ref<Module>, isProjectCreatedWithWizard: Boolean) {
    if (isProjectCreatedWithWizard) {
      return
    }

    StartupManager.getInstance(project).runAfterOpened {
      PyWelcome.welcomeUser(project, baseDir, moduleRef.get(), false)
    }
  }
}

internal object PyWelcomeGenerator {
  fun createWelcomeSettingsPanel(): JPanel {
    return CheckBoxWithDescription(
      JBCheckBox(PyWelcomeBundle.message("py.welcome.new.project.text"),
                 PyWelcomeSettings.instance.createWelcomeScriptForEmptyProject).apply {
        addItemListener { e -> PyWelcomeSettings.instance.createWelcomeScriptForEmptyProject = e.stateChange == ItemEvent.SELECTED }
      },
      PyWelcomeBundle.message("py.welcome.new.project.description")
    )
  }

  fun welcomeUser(project: Project, baseDir: VirtualFile, module: Module) {
    PyWelcome.welcomeUser(project, baseDir, module, true)
  }
}

private object PyWelcome {
  private val LOG = Logger.getInstance(PyWelcome::class.java)

  @CalledInAny
  fun welcomeUser(project: Project, baseDir: VirtualFile, module: Module?, newProject: Boolean) {
    val enabled = PyWelcomeSettings.instance.createWelcomeScriptForEmptyProject

    PyWelcomeCollector.logWelcomeProject(project, if (newProject) ProjectType.NEW else ProjectType.OPENED)

    if (enabled &&
        isEmptyProject(project, baseDir, module).also { if (!it) PyWelcomeCollector.logWelcomeScript(project, ScriptResult.NOT_EMPTY) }) {
      prepareFileAndOpen(project, baseDir).onSuccess {
        if (it != null) {
          // expand tree after the welcome script is created, otherwise expansion will have no effect on empty tree
          expandProjectTree(project, ToolWindowManager.getInstance(project))
          createRunConfiguration(project, it)
        }
      }
    }
    else {
      expandProjectTree(project, ToolWindowManager.getInstance(project))
    }
  }

  private fun isEmptyProject(project: Project, baseDir: VirtualFile, module: Module?): Boolean {
    val sdkBinary = (module?.pythonSdk ?: project.pythonSdk)?.homeDirectory
    val innerSdk = sdkBinary != null && VfsUtil.isAncestor(baseDir, sdkBinary, true)

    return baseDir.children.all {
      ProjectCoreUtil.isProjectOrWorkspaceFile(it) ||
      (innerSdk && it.isDirectory && VfsUtil.isAncestor(it, sdkBinary!!, true))
    }
  }

  private fun prepareFileAndOpen(project: Project, baseDir: VirtualFile): CancellablePromise<PsiFile?> {
    return AppUIExecutor
      .onWriteThread()
      .expireWith(project)
      .submit(
        Callable<PsiFile?> {
          WriteAction.compute<PsiFile?, Exception> {
            prepareFile(project, baseDir)?.also {
              AppUIExecutor.onUiThread().expireWith(project).execute { it.navigate(true) }
            }
          }
        }
      )
  }

  private fun createRunConfiguration(project: Project, file: PsiFile) {
    RunConfigurationProducer
      .getInstance(PythonRunConfigurationProducer::class.java)
      .createConfigurationFromContext(ConfigurationContext(file))
      ?.let {
        RunManager.getInstance(project).addConfiguration(it.configurationSettings)
      }
  }

  @CalledInAny
  private fun expandProjectTree(project: Project, toolWindowManager: ToolWindowManager) {
    // the approach was taken from com.intellij.platform.PlatformProjectViewOpener

    val toolWindow = toolWindowManager.getToolWindow(ToolWindowId.PROJECT_VIEW)
    if (toolWindow == null) {
      val listener = ProjectViewListener(project)
      // collected listener will release the connection
      project.messageBus.connect(listener).subscribe(ToolWindowManagerListener.TOPIC, listener)
    }
    else {
      StartupManager.getInstance(project).runAfterOpened(
        DumbAwareRunnable {
          AppUIExecutor
            .onUiThread(ModalityState.NON_MODAL)
            .expireWith(project)
            .submit {
              val pane = ProjectView.getInstance(project).getProjectViewPaneById(ProjectViewPane.ID)
              if (pane == null) {
                LOG.warn("Project view pane is null")
                PyWelcomeCollector.logWelcomeProjectView(project, ProjectViewResult.NO_PANE)
                return@submit
              }

              val tree = pane.tree
              if (tree == null) {
                LOG.warn("Project view tree is null")
                PyWelcomeCollector.logWelcomeProjectView(project, ProjectViewResult.NO_TREE)
                return@submit
              }

              PyWelcomeCollector.logWelcomeProjectView(project, ProjectViewResult.EXPANDED)
              TreeUtil.expand(tree, 2)
            }
        }
      )
    }
  }

  private fun prepareFile(project: Project, baseDir: VirtualFile): PsiFile? {
    val file = kotlin.runCatching { baseDir.createChildData(this, "main.py") }
      .onFailure { PyWelcomeCollector.logWelcomeScript(project, ScriptResult.NO_VFILE) }
      .getOrThrow()

    val psiFile = PsiManager.getInstance(project).findFile(file)
    if (psiFile == null) {
      LOG.warn("Unable to get psi for $file")
      PyWelcomeCollector.logWelcomeScript(project, ScriptResult.NO_PSI)
      return null
    }

    writeText(project, psiFile)?.also { line ->
      PyWelcomeCollector.logWelcomeScript(project, ScriptResult.CREATED)

      XDebuggerUtil.getInstance().toggleLineBreakpoint(project, file, line)
    }

    return psiFile
  }

  private fun writeText(project: Project, psiFile: PsiFile): Int? {
    val document = PsiDocumentManager.getInstance(project).getDocument(psiFile)
    if (document == null) {
      LOG.warn("Unable to get document for ${psiFile.virtualFile}")
      PyWelcomeCollector.logWelcomeScript(project, ScriptResult.NO_DOCUMENT)
      return null
    }

    val languageLevel = LanguageLevel.forElement(psiFile)

    val greeting = if (languageLevel.isAtLeast(LanguageLevel.PYTHON36)) "f'Hi, {name}'" else "\"Hi, {0}\".format(name)"

    val breakpointLine = if (languageLevel.isPython2) 9 else 8
    val toggleBreakpointTip = PyWelcomeBundle.message("py.welcome.script.toggle.breakpoint",
                                                      getShortcutText(IdeActions.ACTION_TOGGLE_LINE_BREAKPOINT))

    // search everywhere is not initialized until its first usage happens
    val searchEverywhereShortcut = IdeBundle.message("double.ctrl.or.shift.shortcut",
                                                     if (SystemInfo.isMac) MacKeymapUtil.SHIFT else "Shift")

    document.setText(
      (if (languageLevel.isPython2) "# coding=utf-8\n" else "") +
      """
        # ${PyWelcomeBundle.message("py.welcome.script.header")}
        
        # ${PyWelcomeBundle.message("py.welcome.script.run.or.type", getShortcutText(IdeActions.ACTION_DEFAULT_RUNNER))}
        # ${PyWelcomeBundle.message("py.welcome.script.search.everywhere", searchEverywhereShortcut)}


        def print_hi(name):
            # ${PyWelcomeBundle.message("py.welcome.script.use.breakpoint")}
            print($greeting)  # $toggleBreakpointTip


        # ${PyWelcomeBundle.message("py.welcome.script.run")}
        if __name__ == '__main__':
            print_hi('PyCharm')

        # ${PyWelcomeBundle.message("py.welcome.script.help")}
        
      """.trimIndent()
    )

    PsiDocumentManager.getInstance(project).commitDocument(document)

    return breakpointLine
  }

  private class ProjectViewListener(private val project: Project) : ToolWindowManagerListener, Disposable {

    private var toolWindowRegistered = false

    override fun toolWindowsRegistered(ids: List<String>, toolWindowManager: ToolWindowManager) {
      if (ToolWindowId.PROJECT_VIEW in ids) {
        toolWindowRegistered = true
        Disposer.dispose(this) // to release message bus connection
        expandProjectTree(project, toolWindowManager)
      }
    }

    override fun dispose() {
      if (!toolWindowRegistered) {
        PyWelcomeCollector.logWelcomeProjectView(project, ProjectViewResult.NO_TOOLWINDOW)
      }
    }
  }
}