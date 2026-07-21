// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.ide.actions.ReportFeedbackService
import com.intellij.ide.actions.SendFeedbackAction
import com.intellij.ide.plugins.PluginManager
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.ex.ComboBoxAction
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.wm.IdeFrame
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi
import com.intellij.toolWindow.InternalDecoratorImpl
import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunManager
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.installAndEnable
import com.jetbrains.python.PyBundle
import com.jetbrains.python.run.AbstractPythonRunConfiguration
import com.jetbrains.python.sdk.legacy.PythonSdkUtil
import com.intellij.ui.ColorUtil
import com.intellij.util.ui.JBUI
import com.jetbrains.python.sdk.findPythonSdk
import kotlinx.coroutines.launch
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebugSessionListener
import com.intellij.xdebugger.XDebuggerManager
import com.jetbrains.python.icons.PythonIcons
import org.jetbrains.annotations.ApiStatus
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import java.net.URLEncoder
import javax.swing.JComponent
import javax.swing.SwingUtilities

internal const val PYTHON_DAP_PLUGIN_ID = "intellij.python.dap.plugin"
private const val REPORT_DEBUGPY_ISSUE_URL =
  "https://youtrack.jetbrains.com/newIssue?project=PY&summary=%5Bdebugpy%5D&c=Subsystem+Debugger"
private const val ISSUE_DESCRIPTION_FALLBACK_SYS_INFO = "PyCharm version:\nOS:"
private const val DEFAULT_ISSUE_LOG_INSTRUCTION =
  "To collect debugpy adapter logs, enable logging via Help | Diagnostic Tools | debugpy Logging, reproduce the issue, and attach the logs."
private const val ISSUE_DESCRIPTION_TEMPLATE =
  "Describe the issue:\n\nSteps to reproduce:\n\n\n%1\$s\n\nPlease attach the IDE log file to this issue (Help | Show Log in Finder/Explorer).\n%2\$s\nYou can use Help | Collect Logs and Diagnostic Data to create a zip with all logs."

internal class PyDebuggerBackendSwitcherAction : ComboBoxAction(), DumbAware {
  private val buttonRefs = mutableListOf<WeakReference<JComponent>>()

  init {
    templatePresentation.setText(PyBundle.messagePointer("action.Python.DebuggerBackendSwitcher.text"))
  }

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    return super.createCustomComponent(presentation, place).also { component ->
      synchronized(buttonRefs) {
        buttonRefs.removeAll { it.get() == null }
        buttonRefs.add(WeakReference(component))
      }
    }
  }

  internal fun getButton(project: Project): JComponent? {
    val frame = WindowManager.getInstance().getFrame(project)
    synchronized(buttonRefs) {
      buttonRefs.removeAll { it.get() == null }
      return buttonRefs.mapNotNull { it.get() }
        .firstOrNull { SwingUtilities.getWindowAncestor(it) == frame }
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun shouldShowDisabledActions(): Boolean = true

  override fun update(e: AnActionEvent) {
    val project = e.project
    if (project == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    if (!PyDebuggerBackendSwitcherVisibilityPin.isPinned(project)) {
      val switchHandler = PyDebuggerBackendSwitchHandler.EP_NAME.extensionList.firstOrNull {
        it.ownsRunProfile(e)
      }
      if (switchHandler != null && !switchHandler.shouldShowSwitcher(project, e)) {
        e.presentation.isEnabledAndVisible = false
        return
      }
    }

    e.presentation.isEnabledAndVisible = true

    val storedBackend = PyDebuggerOptionsProvider.getInstance(project).selectedBackend
    val effectiveBackend = resolveEffectiveBackend(storedBackend, isDebugpyAvailableInProject(project))

    e.presentation.setText(
      buildModeText(PyBundle.message("debugger.backend.switcher.label"),
                    pyDebuggerBackendDisplayName(effectiveBackend)), false)
  }

  override fun createPopupActionGroup(button: JComponent, dataContext: DataContext): DefaultActionGroup = createGroup()

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    if (WindowManager.getInstance().getFrame(project) !is IdeFrame) {
      val popup = createActionPopup(createGroup(), e.dataContext, null)
      val component = e.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT) as? JComponent
      if (component != null) {
        popup.showUnderneathOf(component)
      }
      else {
        popup.showInBestPositionFor(e.dataContext)
      }
    }
    else {
      super.actionPerformed(e)
    }
  }

  private fun createGroup(): DefaultActionGroup {
    val group = DefaultActionGroup()
    group.add(SelectBackendAction(PyDebuggerBackend.PYDEVD))
    group.add(SelectBackendAction(PyDebuggerBackend.DEBUGPY))
    group.add(InstallPythonDapDebuggerPluginAction())
    group.addSeparator()
    group.add(ReportDebugpyIssueAction())
    return group
  }

  private inner class SelectBackendAction(private val backend: PyDebuggerBackend) : AnAction(pyDebuggerBackendDisplayName(backend)), DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
      val project = e.project ?: return
      val storedBackend = PyDebuggerOptionsProvider.getInstance(project).selectedBackend
      val effectiveBackend = resolveEffectiveBackend(storedBackend, isDebugpyAvailableInProject(project))
      e.presentation.isEnabled = true
      e.presentation.putClientProperty(
        ActionUtil.TOOLTIP_TEXT,
        PyBundle.message(if (backend == PyDebuggerBackend.DEBUGPY) "debugger.backend.debugpy.description" else "debugger.backend.pydevd.description")
      )
      if (backend == PyDebuggerBackend.DEBUGPY && !isDebugpyAvailableInProject(project)) {
        e.presentation.isEnabled = false
        @Suppress("DialogTitleCapitalization")
        val disabledMsg = if (!isPythonDapPluginInstalledAndEnabled()) {
          PyBundle.message("debugger.backend.debugpy.disabled.not.installed.tooltip")
        }
        else {
          val sdk = getEffectiveSdk(project)
          if (sdk != null && PythonSdkUtil.isRemote(sdk)) {
            PyBundle.message("debugger.backend.debugpy.disabled.remote.tooltip")
          }
          else {
            PyBundle.message("debugger.backend.debugpy.disabled.tooltip")
          }
        }
        e.presentation.description = disabledMsg
        e.presentation.putClientProperty(ActionUtil.TOOLTIP_TEXT, disabledMsg)
      }
      e.presentation.icon = if (effectiveBackend == backend) AllIcons.Actions.Checked else null
    }

    override fun actionPerformed(e: AnActionEvent) {
      if (backend == PyDebuggerBackend.DEBUGPY && !isPythonDapPluginInstalledAndEnabled()) return
      e.project?.let { switchBackend(it, backend) }
    }
  }

  private class InstallPythonDapDebuggerPluginAction : AnAction(
    PyBundle.message("debugger.backend.install.debugpy.plugin"),
    null,
    AllIcons.Actions.Download,
  ), DumbAware {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
      e.presentation.isEnabledAndVisible = !isPythonDapPluginInstalledAndEnabled()
    }

    override fun actionPerformed(e: AnActionEvent) {
      val project = e.project ?: return
      installPythonDapPlugin(project)
    }
  }

  @Suppress("DialogTitleCapitalization")
  private class ReportDebugpyIssueAction : AnAction(
    PyBundle.message("debugger.backend.report.issue.debugpy"),
    null,
    PythonIcons.Debugger.Youtrack
  ), DumbAware {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
      e.presentation.isEnabledAndVisible = isPythonDapPluginInstalledAndEnabled()
    }

    override fun actionPerformed(e: AnActionEvent) {
      val project = e.project
      PyDebuggerBackendSwitchHandler.EP_NAME.extensionList.forEach { it.onReportIssueClicked(project) }
      service<ReportFeedbackService>().coroutineScope.launch {
        val url = REPORT_DEBUGPY_ISSUE_URL + "&description=" + URLEncoder.encode(buildIssueDescription(project), Charsets.UTF_8)
        BrowserUtil.browse(url, project)
      }
    }

    private suspend fun buildIssueDescription(project: Project?): String {
      val sysInfo = if (project != null) {
        SendFeedbackAction.getDescription(project).trim()
      }
      else {
        ISSUE_DESCRIPTION_FALLBACK_SYS_INFO
      }
      val logInstruction = project?.let { p ->
        PyDebuggerBackendSwitchHandler.EP_NAME.extensionList.firstNotNullOfOrNull { it.getReportIssueLogInstruction(p) }
      } ?: DEFAULT_ISSUE_LOG_INSTRUCTION
      return ISSUE_DESCRIPTION_TEMPLATE.format(sysInfo, logInstruction)
    }
  }

}

/**
 * Returns the SDK of the currently selected run configuration, falling back to the project SDK.
 */
private fun getEffectiveSdk(project: Project): Sdk? =
  (RunManager.getInstance(project).selectedConfiguration?.configuration as? AbstractPythonRunConfiguration<*>)?.sdk
  ?: ProjectRootManager.getInstance(project).projectSdk

@ApiStatus.Internal
fun installPythonDapPlugin(project: Project, onLoaded: Runnable? = null) {
  project.service<PyDapPluginLoadListener>().listen(onLoaded)
  installAndEnable(project, setOf(PluginId.getId(PYTHON_DAP_PLUGIN_ID)), showDialog = true) {}
}

// This method exists to provide backward compatibility with older versions of the DAP plugin
// We can't use the switcher handler for session restart, since older plugin doesn't connect to the new EP
// TODO: Session restart code should be removed after newer version of DAP plugin (without switcher handler EP) is released
@ApiStatus.Internal
fun afterInstallSwitchDebugpy() {
  for (openProject in ProjectManager.getInstance().openProjects) {
    if (PyDebuggerBackendSwitchHandler.EP_NAME.extensionList.any { it.isApplicable(openProject) }) {
      val switchConfirmed = switchBackendUsingHandler(openProject, PyDebuggerBackend.DEBUGPY)
      if (!switchConfirmed) {
        PyDebuggerOptionsProvider.getInstance(openProject).selectedBackend = PyDebuggerBackend.PYDEVD
      }
    } else {
      promptUserToSwitchToDebugpy(openProject)
    }
  }
}

@Suppress("DialogTitleCapitalization")
private fun promptUserToSwitchToDebugpy(openProject: Project) {
  val eligibleSessions = findDebugpyEligibleSessions(openProject)
  if (eligibleSessions.isEmpty()) {
    PyDebuggerOptionsProvider.getInstance(openProject).selectedBackend = PyDebuggerBackend.DEBUGPY
    return
  }

  val switchNow = MessageDialogBuilder.yesNo(
    PyBundle.message("debugger.backend.switch.after.install.title"),
    PyBundle.message("debugger.backend.switch.after.install.message"),
  )
    .yesText(PyBundle.message("debugger.backend.switch.after.install.switch.and.restart"))
    .noText(PyBundle.message("debugger.backend.switch.after.install.switch.later"))
    .ask(openProject)
  if (switchNow) {
    PyDebuggerOptionsProvider.getInstance(openProject).selectedBackend = PyDebuggerBackend.DEBUGPY
    restartDebugSessions(eligibleSessions)
  } else {
    PyDebuggerOptionsProvider.getInstance(openProject).selectedBackend = PyDebuggerBackend.PYDEVD
  }
}

private fun findDebugpyEligibleSessions(project: Project): List<XDebugSession> {
  return XDebuggerManager.getInstance(project).debugSessions.filter { session ->
    val config = session.runProfile as? AbstractPythonRunConfiguration<*> ?: return@filter false
    val sdk = config.sdk ?: return@filter false
    isDebugpyAvailableForSdk(sdk, project)
  }
}

private fun restartDebugSessions(sessions: List<XDebugSession>) {
  for (session in sessions) {
    val settings = session.executionEnvironment?.runnerAndConfigurationSettings ?: continue
    session.addSessionListener(object : XDebugSessionListener {
      override fun sessionStopped() {
        ApplicationManager.getApplication().invokeLater {
          ProgramRunnerUtil.executeConfiguration(settings, DefaultDebugExecutor.getDebugExecutorInstance())
        }
      }
    })
    session.stop()
  }
}

internal fun isPythonDapPluginInstalledAndEnabled(): Boolean {
  val pluginId = PluginId.getId(PYTHON_DAP_PLUGIN_ID)
  return PluginManager.isPluginInstalled(pluginId) && !PluginManagerCore.isDisabled(pluginId)
}

private fun switchBackend(project: Project, backend: PyDebuggerBackend) {
  val fromBackend = PyDebuggerOptionsProvider.getInstance(project).selectedBackend
  if (fromBackend == backend) return
  switchBackendUsingHandler(project, backend)
}

/**
 * Switches the Python debugger backend for the given project using available backend switch handlers.
 * The method ensures that all applicable handlers approve of the switch before applying the new backend
 * and notifies each handler to manage their sessions accordingly after the switch.
 *
 * @param project The current project where the backend switch should be applied.
 * @param backend The new Python debugger backend to be set for the project.
 */
private fun switchBackendUsingHandler(project: Project, backend: PyDebuggerBackend): Boolean {
  val fromBackend = PyDebuggerOptionsProvider.getInstance(project).selectedBackend
  val handlers = PyDebuggerBackendSwitchHandler.EP_NAME.extensionList.filter { it.isApplicable(project) }
  if (!handlers.all { it.confirmSwitch(project) }) return false
  PyDebuggerOptionsProvider.getInstance(project).selectedBackend = backend
  handlers.forEach { it.handleSessions(project) }
  handlers.forEach { it.onBackendSwitched(project, fromBackend, backend) }
  return true
}

@NlsSafe
private fun buildModeText(modeLabel: String, backendName: String): String =
  "<html><font color='#${ColorUtil.toHex(JBUI.CurrentTheme.Label.disabledForeground())}'>$modeLabel</font> $backendName</html>"


@NlsSafe
internal fun pyDebuggerBackendDisplayName(backend: PyDebuggerBackend): String = when (backend) {
  PyDebuggerBackend.DEBUGPY -> "debugpy"
  PyDebuggerBackend.PYDEVD -> "pydevd"
}

/**
 * Handler-aware per-SDK rule used by the backend-switcher UI.
 *
 * SDKs are accepted when at least one [PyDebuggerBackendSwitchHandler] extension
 * whitelists them.
 */
internal fun isDebugpyAvailableForSdk(sdk: Sdk, project: Project): Boolean {
  return PyDebuggerBackendSwitchHandler.EP_NAME.extensionList.any { it.isDebugpyAvailableForSdk(project, sdk) }
}

/**
 * debugpy is available for [project] when either:
 *  - the currently selected run configuration explicitly specifies an SDK that supports it, or
 *  - the project's Python interpreter (any module SDK; falling back to the project SDK) supports it.
 *
 * The run-configuration SDK takes precedence; if it is present but unsupported, debugpy is not
 * available for the selected configuration. If the selected configuration has no SDK, we still
 * allow debugpy as long as the project interpreter supports it. Used by the backend-switcher UI to
 * decide whether to gray out the **debugpy** entry.
 */
internal object PyDebuggerBackendSwitcherVisibilityPin {
  private val pins = ConcurrentHashMap<Project, Int>()

  inline fun <T> pinVisible(project: Project, block: () -> T): T {
    acquire(project)
    try {
      return block()
    }
    finally {
      release(project)
    }
  }

  @PublishedApi
  internal fun acquire(project: Project) {
    pins.merge(project, 1) { old, _ -> old + 1 }
    syncHeaderToolbarLater(project)
  }

  @PublishedApi
  internal fun release(project: Project) {
    pins.compute(project) { _, old -> if (old == null || old <= 1) null else old - 1 }
    syncHeaderToolbarLater(project)
  }

  fun isPinned(project: Project): Boolean = pins.containsKey(project)

  private fun syncHeaderToolbarLater(project: Project) {
    ApplicationManager.getApplication().invokeLater(
      { syncHeaderToolbar(project) },
      { project.isDisposed },
    )
  }

  private fun syncHeaderToolbar(project: Project) {
    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.DEBUG) ?: return
    val component = toolWindow.component
    val target: Boolean? = if (isPinned(project)) true else null
    val current = component.getClientProperty(ToolWindowContentUi.DONT_HIDE_TOOLBAR_IN_HEADER)
    if (current == target) return
    component.putClientProperty(ToolWindowContentUi.DONT_HIDE_TOOLBAR_IN_HEADER, target)
    ((toolWindow as? ToolWindowEx)?.decorator as? InternalDecoratorImpl)?.updateActiveAndHoverState()
  }
}

@ApiStatus.Internal
fun isDebugpyAvailableInProject(project: Project): Boolean {
  val configSdk = (RunManager.getInstance(project).selectedConfiguration?.configuration as? AbstractPythonRunConfiguration<*>)?.sdk
  if (configSdk != null) return isDebugpyAvailableForSdk(configSdk, project)

  val supportingModuleExists = runBlockingCancellable {
    ModuleManager.getInstance(project).modules.any {
      it.findPythonSdk()?.let { sdk -> isDebugpyAvailableForSdk(sdk, project) } ?: false
    }
  }
  if (supportingModuleExists) return true

  val projectInterpreter = ProjectRootManager.getInstance(project).projectSdk
  return projectInterpreter != null && isDebugpyAvailableForSdk(projectInterpreter, project)
}
