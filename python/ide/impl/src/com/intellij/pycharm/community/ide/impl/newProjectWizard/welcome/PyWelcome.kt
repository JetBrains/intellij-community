// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pycharm.community.ide.impl.newProjectWizard.welcome

import com.intellij.execution.RunManager
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.RunConfigurationProducer
import com.intellij.ide.IdeBundle
import com.intellij.ide.impl.ProjectViewSelectInTarget
import com.intellij.ide.projectView.impl.ProjectViewPane
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.AppUIExecutor
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.client.ClientSystemInfo
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileChooser.FileElement
import com.intellij.openapi.keymap.KeymapUtil.getShortcutText
import com.intellij.openapi.keymap.MacKeymapUtil
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.DumbAwareRunnable
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectCoreUtil
import com.intellij.openapi.startup.StartupManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.platform.DirectoryProjectConfigurator
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.pycharm.community.ide.impl.newProjectWizard.welcome.PyWelcomeCollector.ProjectType
import com.intellij.pycharm.community.ide.impl.newProjectWizard.welcome.PyWelcomeCollector.ProjectViewPoint
import com.intellij.pycharm.community.ide.impl.newProjectWizard.welcome.PyWelcomeCollector.ProjectViewResult
import com.intellij.pycharm.community.ide.impl.newProjectWizard.welcome.PyWelcomeCollector.RunConfigurationResult
import com.intellij.pycharm.community.ide.impl.newProjectWizard.welcome.PyWelcomeCollector.ScriptResult
import com.intellij.pycharm.community.ide.impl.newProjectWizard.welcome.PyWelcomeCollector.logWelcomeRunConfiguration
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import com.intellij.xdebugger.XDebuggerUtil
import com.jetbrains.python.PythonPluginDisposable
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.run.PythonRunConfigurationProducer
import com.jetbrains.python.sdk.pythonSdk
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.CalledInAny
import org.jetbrains.concurrency.CancellablePromise
import java.util.concurrent.Callable

internal class PyWelcomeConfigurator : DirectoryProjectConfigurator {
  override val isEdtRequired: Boolean
    get() = false

  override fun configureProject(project: Project, baseDir: VirtualFile, moduleRef: Ref<Module>, isProjectCreatedWithWizard: Boolean) {
    if (isProjectCreatedWithWizard || isInsideTempDirectory(baseDir)) {
      return
    }

    StartupManager.getInstance(project).runAfterOpened(
      DumbAwareRunnable {
        PyWelcomeCollector.logWelcomeProject(project, ProjectType.OPENED)
        PyWelcome.welcomeUser(project, baseDir, moduleRef.get())
      }
    )
  }

  private fun isInsideTempDirectory(baseDir: VirtualFile): Boolean {
    val tempDir = LocalFileSystem.getInstance().findFileByPath(FileUtil.getTempDirectory()) ?: return false
    return VfsUtil.isAncestor(tempDir, baseDir, true)
  }
}

internal object PyWelcome {
  private val LOG = Logger.getInstance(PyWelcome::class.java)

  @CalledInAny
  fun welcomeUser(project: Project, baseDir: VirtualFile, module: Module?) {
    val enabled = PyWelcomeSettings.instance.createWelcomeScriptForEmptyProject

    if (isEmptyProject(project, baseDir, module)) {
      if (enabled) {
        prepareFileAndOpen(project, baseDir).onSuccess {
          if (it != null) {
            // expand tree after the welcome script is created, otherwise expansion will have no effect on empty tree
            expandProjectTree(project, baseDir, module, it.virtualFile)
            createRunConfiguration(project, it)
          }
        }
      }
      else {
        PyWelcomeCollector.logWelcomeScript(project, ScriptResult.DISABLED_BUT_COULD)
        expandProjectTree(project, baseDir, module, null)
      }
    }
    else {
      PyWelcomeCollector.logWelcomeScript(project, if (enabled) ScriptResult.NOT_EMPTY else ScriptResult.DISABLED_AND_COULD_NOT)
      expandProjectTree(project, baseDir, module, null)
    }
  }

  private fun isEmptyProject(project: Project, baseDir: VirtualFile, module: Module?): Boolean {
    return firstUserFile(project, baseDir, module) == null
  }

  private fun firstUserFile(project: Project, baseDir: VirtualFile, module: Module?): VirtualFile? {
    if (module != null && module.isDisposed || module == null && project.isDisposed) return null

    val sdkBinary = (module?.pythonSdk ?: project.pythonSdk)?.homeDirectory
    val innerSdk = sdkBinary != null && VfsUtil.isAncestor(baseDir, sdkBinary, true)

    return baseDir.children.filterNot {
      ProjectCoreUtil.isProjectOrWorkspaceFile(it) ||
      innerSdk && it.isDirectory && VfsUtil.isAncestor(it, sdkBinary!!, true) ||
      FileElement.isFileHidden(it)
    }.firstOrNull()
  }

  private fun prepareFileAndOpen(project: Project, baseDir: VirtualFile): CancellablePromise<PsiFile?> {
    return AppUIExecutor
      .onWriteThread()
      .expireWith(PythonPluginDisposable.getInstance(project))
      .submit(
        Callable {
          WriteAction.compute<PsiFile?, Exception> {
            prepareFile(project, baseDir)?.also {
              AppUIExecutor.onUiThread().expireWith(PythonPluginDisposable.getInstance(project)).execute { it.navigate(true) }
            }
          }
        }
      )
  }

  private fun createRunConfiguration(project: Project, file: PsiFile) {
    RunConfigurationProducer
      .getInstance(PythonRunConfigurationProducer::class.java)
      .createConfigurationFromContext(ConfigurationContext(file))
      .also {
        logWelcomeRunConfiguration(project, if (it == null) RunConfigurationResult.NULL else RunConfigurationResult.CREATED)
      }
      ?.let {
        val settings = it.configurationSettings
        val runManager = RunManager.getInstance(project)
        runManager.addConfiguration(settings)
        runManager.selectedConfiguration = settings
      }
  }

  @CalledInAny
  private fun expandProjectTree(project: Project, baseDir: VirtualFile, module: Module?, file: VirtualFile?) {
    expandProjectTree(project, ToolWindowManager.getInstance(project), baseDir, module, file, ProjectViewPoint.IMMEDIATELY)
  }

  @CalledInAny
  private fun expandProjectTree(
    project: Project,
    toolWindowManager: ToolWindowManager,
    baseDir: VirtualFile,
    module: Module?,
    file: VirtualFile?,
    point: ProjectViewPoint,
  ) {
    // the approach was taken from com.intellij.platform.PlatformProjectViewOpener

    val toolWindow = toolWindowManager.getToolWindow(ToolWindowId.PROJECT_VIEW)
    if (toolWindow == null) {
      val listener = ProjectViewListener(project, baseDir, module, file)
      Disposer.register(PythonPluginDisposable.getInstance(project), listener)
      // collected listener will release the connection
      project.messageBus.connect(listener).subscribe(ToolWindowManagerListener.TOPIC, listener)
    }
    else {
      StartupManager.getInstance(project).runAfterOpened(
        DumbAwareRunnable {
          AppUIExecutor
            .onUiThread(ModalityState.nonModal())
            .expireWith(PythonPluginDisposable.getInstance(project))
            .submit {
              val fileToChoose = (file ?: firstUserFile(project, baseDir, module)) ?: return@submit

              ProjectViewSelectInTarget
                .select(project, fileToChoose, ProjectViewPane.ID, null, fileToChoose, false)
                .doWhenDone { PyWelcomeCollector.logWelcomeProjectView(project, point, ProjectViewResult.EXPANDED) }
                .doWhenRejected(Runnable { PyWelcomeCollector.logWelcomeProjectView(project, point, ProjectViewResult.REJECTED) })
            }
        }
      )
    }
  }

  @RequiresWriteLock
  fun prepareFile(project: Project, baseDir: VirtualFile): PsiFile {
    val file = kotlin.runCatching { baseDir.createChildData(this, "main.py") }
      .onFailure { PyWelcomeCollector.logWelcomeScript(project, ScriptResult.NO_VFILE) }
      .getOrThrow()

    val psiFile = PsiManager.getInstance(project).findFile(file) ?: error("File $file was just created, but not found in PSI")

    writeText(psiFile)?.also { line ->
      PyWelcomeCollector.logWelcomeScript(project, ScriptResult.CREATED)

      XDebuggerUtil.getInstance().toggleLineBreakpoint(project, file, line)
    }

    return psiFile
  }


  @RequiresWriteLock
  internal fun writeText(psiFile: PsiFile): Int? {
    val project = psiFile.project
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
                                                     if (ClientSystemInfo.isMac()) MacKeymapUtil.SHIFT else "Shift")

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

  private class ProjectViewListener(
    private val project: Project,
    private val baseDir: VirtualFile,
    private val module: Module?,
    private val file: VirtualFile?,
  ) : ToolWindowManagerListener, Disposable {

    private var toolWindowRegistered = false

    override fun toolWindowsRegistered(ids: List<String>, toolWindowManager: ToolWindowManager) {
      if (ToolWindowId.PROJECT_VIEW in ids) {
        toolWindowRegistered = true
        Disposer.dispose(this) // to release message bus connection
        expandProjectTree(project, toolWindowManager, baseDir, module, file, ProjectViewPoint.FROM_LISTENER)
      }
    }

    override fun dispose() {
      if (!toolWindowRegistered) {
        PyWelcomeCollector.logWelcomeProjectView(project, ProjectViewPoint.FROM_LISTENER, ProjectViewResult.NO_TOOLWINDOW)
      }
    }
  }
}