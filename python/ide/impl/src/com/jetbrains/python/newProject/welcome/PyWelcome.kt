// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.newProject.welcome

import com.intellij.execution.RunManager
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.RunConfigurationProducer
import com.intellij.ide.IdeBundle
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.AppUIExecutor
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
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.DirectoryProjectConfigurator
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.ui.components.JBCheckBox
import com.intellij.xdebugger.XDebuggerUtil
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.run.PythonRunConfigurationProducer
import org.jetbrains.annotations.CalledInAny
import org.jetbrains.concurrency.CancellablePromise
import java.awt.event.ItemEvent
import java.util.concurrent.Callable
import javax.swing.JPanel


class PyWelcomeConfigurator : DirectoryProjectConfigurator {

  override fun configureProject(project: Project, baseDir: VirtualFile, moduleRef: Ref<Module>, newProject: Boolean) {
    if (newProject) return

    StartupManager.getInstance(project).runWhenProjectIsInitialized(
      DumbAwareRunnable { PyWelcome.welcomeUser(project, baseDir) }
    )
  }
}

object PyWelcomeGenerator {

  fun createWelcomeSettingsPanel(): JPanel {
    return CheckBoxWithDescription(
      JBCheckBox(PyWelcomeBundle.message("py.welcome.new.project.text"),
                 PyWelcomeSettings.instance.createWelcomeScriptForEmptyProject).apply {
        addItemListener { e -> PyWelcomeSettings.instance.createWelcomeScriptForEmptyProject = e.stateChange == ItemEvent.SELECTED }
      },
      PyWelcomeBundle.message("py.welcome.new.project.description")
    )
  }

  fun welcomeUser(project: Project, baseDir: VirtualFile) = PyWelcome.welcomeUser(project, baseDir)
}

private object PyWelcome {

  private val LOG = Logger.getInstance(PyWelcome::class.java)

  @CalledInAny
  fun welcomeUser(project: Project, baseDir: VirtualFile) {
    if (PyWelcomeSettings.instance.createWelcomeScriptForEmptyProject &&
        baseDir.children.filterNot { ProjectCoreUtil.isProjectOrWorkspaceFile(it) }.isEmpty()) {
      prepareFileAndOpen(project, baseDir).onSuccess {
        if (it != null) createRunConfiguration(project, it)
      }
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

  private fun prepareFile(project: Project, baseDir: VirtualFile): PsiFile? {
    val file = baseDir.createChildData(this, "main.py")

    val psiFile = PsiManager.getInstance(project).findFile(file)
    if (psiFile == null) {
      LOG.warn("Unable to get psi for $file")
      return null
    }

    writeText(project, psiFile)?.also { line ->
      XDebuggerUtil.getInstance().toggleLineBreakpoint(project, file, line)
    }

    return psiFile
  }

  private fun writeText(project: Project, psiFile: PsiFile): Int? {
    val document = PsiDocumentManager.getInstance(project).getDocument(psiFile)
    if (document == null) {
      LOG.warn("Unable to get document for ${psiFile.virtualFile}")
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
}