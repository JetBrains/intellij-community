// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger

import com.intellij.execution.Executor
import com.intellij.execution.ui.RunContentDescriptor
import com.intellij.execution.ui.RunContentManager
import com.intellij.execution.ui.RunContentWithExecutorListener
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.ui.GotItComponentBuilder
import com.intellij.ui.GotItTooltip
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerManagerListener
import com.jetbrains.python.PyBundle
import com.jetbrains.python.run.AbstractPythonRunConfiguration
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import org.intellij.lang.annotations.Language
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JComponent
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds


internal class PyDapPluginInstallGotItListener : XDebuggerManagerListener {
  override fun processStarted(debugProcess: XDebugProcess) {
    val session = debugProcess.session
    if (session.runProfile !is AbstractPythonRunConfiguration<*>) return
    if (isPythonDapPluginInstalledAndEnabled()) return
    if (isAcked()) return

    val project = session.project
    val builder = GotItComponentBuilder { PyBundle.message("debugger.dap.plugin.install.got.it.text") }
      .withHeader(PyBundle.message("debugger.dap.plugin.install.got.it.header"))
    showGotItWhenContentSelected(session, builder) {
      findBackendSwitcherButton(project)
    }
  }

  private fun findBackendSwitcherButton(project: Project): JComponent? {
    val action = ActionManager.getInstance().getAction(DEBUGGER_BACKEND_SWITCHER_ACTION_ID) as? PyDebuggerBackendSwitcherAction
    return action?.getButton(project)
  }

  private fun showGotItWhenContentSelected(
    session: XDebugSession,
    builder: GotItComponentBuilder,
    buttonFinder: () -> JComponent?,
  ) {
    val fired = AtomicBoolean(false)
    val project = session.project
    val connection = project.messageBus.connect()
    connection.subscribe(RunContentManager.TOPIC, object : RunContentWithExecutorListener {
      override fun contentSelected(descriptor: RunContentDescriptor?, executor: Executor) {
        if (descriptor?.processHandler !== session.debugProcess.processHandler) return
        if (fired.compareAndSet(false, true)) {
          connection.disconnect()
          PythonDebuggerScope.childScope(project, "PyDapPluginInstallGotItListener").launch {
            PyDebuggerBackendSwitcherVisibilityPin.pinVisible(project) {
              showUntilAcked(buttonFinder, builder)
            }
          }
        }
      }
    })

    PythonDebuggerScope.childScope(project, "PyDapPluginInstallGotItListener").launch(Dispatchers.EDT) {
      yield()
      if (fired.compareAndSet(false, true)) {
        connection.disconnect()
        PyDebuggerBackendSwitcherVisibilityPin.pinVisible(project) {
          showUntilAcked(buttonFinder, builder)
        }
      }
    }
  }

  private suspend fun showUntilAcked(
    buttonFinder: () -> JComponent?,
    builder: GotItComponentBuilder,
  ) {
    withTimeoutOrNull(30.seconds) {
      while (true) {
        val button = withContext(Dispatchers.EDT) { buttonFinder() }
        if (button?.isShowing == true) {
          val userClicked = showAndAwaitClose(button, builder)
          if (userClicked) return@withTimeoutOrNull
          val stillShowing = withContext(Dispatchers.EDT) { button.isShowing }
          if (stillShowing) return@withTimeoutOrNull
          resetGotItCounter()
          delay(100.milliseconds)
        }
        else {
          delay(50.milliseconds)
        }
      }
    }
  }

  private suspend fun showAndAwaitClose(
    button: JComponent,
    builder: GotItComponentBuilder,
  ): Boolean {
    val result = CompletableDeferred<Boolean>()
    withContext(Dispatchers.EDT) {
      if (!button.isShowing) {
        result.complete(false)
        return@withContext
      }
      val tooltip = GotItTooltip(GOT_IT_ID, builder).withShowCount(1)
      tooltip.withGotItButtonAction {
        PropertiesComponent.getInstance().setValue("${GotItTooltip.PROPERTY_PREFIX}.$GOT_IT_ID.ack", true)
        result.complete(true)
      }
      tooltip.setOnBalloonCreated { balloon ->
        balloon.addListener(object : JBPopupListener {
          override fun onClosed(event: LightweightWindowEvent) {
            result.complete(false)
          }
        })
      }
      tooltip.show(button, GotItTooltip.BOTTOM_MIDDLE)
    }
    return result.await()
  }

  private fun isAcked(): Boolean =
    PropertiesComponent.getInstance().getBoolean("${GotItTooltip.PROPERTY_PREFIX}.$GOT_IT_ID.ack", false)

  private fun resetGotItCounter() =
    PropertiesComponent.getInstance().unsetValue("${GotItTooltip.PROPERTY_PREFIX}.$GOT_IT_ID")

  private companion object {
    @Language("devkit-action-id")
    private const val DEBUGGER_BACKEND_SWITCHER_ACTION_ID = "Python.DebuggerBackendSwitcher"
    private const val GOT_IT_ID = "python.debugger.dap.plugin.install.got.it"
  }
}
