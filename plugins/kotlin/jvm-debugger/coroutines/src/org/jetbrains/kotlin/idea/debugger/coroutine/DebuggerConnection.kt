// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.debugger.coroutine

import com.intellij.debugger.DebuggerInvocationUtil
import com.intellij.debugger.engine.JavaDebugProcess
import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.ui.RunnerLayoutUi
import com.intellij.execution.ui.layout.PlaceInGrid
import com.intellij.execution.ui.layout.impl.RunnerContentUi
import com.intellij.execution.ui.layout.impl.RunnerLayoutUiImpl
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.content.Content
import com.intellij.util.messages.MessageBusConnection
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.XDebuggerManagerListener
import com.intellij.xdebugger.impl.XDebugSessionImpl
import org.jetbrains.kotlin.idea.debugger.coroutine.util.CreateContentParamsProvider
import org.jetbrains.kotlin.idea.debugger.coroutine.util.logger
import org.jetbrains.kotlin.idea.debugger.coroutine.view.XCoroutineView
import org.jetbrains.kotlin.utils.addToStdlib.safeAs

class DebuggerConnection(
    val project: Project,
    val configuration: RunConfigurationBase<*>?,
    val params: JavaParameters?,
    modifyArgs: Boolean = true,
    // Used externally on the Android Studio side
    @Suppress("MemberVisibilityCanBePrivate")
    val alwaysShowPanel: Boolean = false
) : XDebuggerManagerListener, Disposable {
    companion object {
        private val log by logger
    }

    private var connection: MessageBusConnection? = null
    private val coroutineAgentAttached: Boolean
    private var isDisposed = false

    init {
        if (params is JavaParameters && modifyArgs) {
            // gradle related logic in KotlinGradleCoroutineDebugProjectResolver
            coroutineAgentAttached = CoroutineAgentConnector.attachCoroutineAgent(project, params, configuration)
        } else {
            coroutineAgentAttached = false
            log.debug("Coroutine debugger disabled.")
        }

        connection = project.messageBus.connect()
        connection?.subscribe(XDebuggerManager.TOPIC, this)
    }

    override fun processStarted(debugProcess: XDebugProcess) {
        DebuggerInvocationUtil.swingInvokeLater(project) {
            val session = debugProcess.session
            if (debugProcess is JavaDebugProcess &&
                !isDisposed &&
                coroutinesPanelShouldBeShown() &&
                !coroutinePanelIsRegistered(session))
            {
                registerXCoroutinesPanel(session)?.let {
                    Disposer.register(this, it)
                }
            }
        }
    }

    private fun coroutinePanelIsRegistered(session: XDebugSession): Boolean {
        val ui = session.ui.safeAs<RunnerLayoutUiImpl>() ?: return false
        val runnerContentUi = RunnerContentUi.KEY.getData(ui) ?: return false
        val content = invokeAndWaitIfNeeded {
            runnerContentUi.findContent(CoroutineDebuggerContentInfo.XCOROUTINE_THREADS_CONTENT)
        }
        return content != null
    }

    override fun processStopped(debugProcess: XDebugProcess) {
        ApplicationManager.getApplication().invokeLater {
            Disposer.dispose(this)
        }
    }

    private fun registerXCoroutinesPanel(session: XDebugSession): Disposable? {
        val ui = session.ui ?: return null
        val xCoroutineThreadView = XCoroutineView(project, session as XDebugSessionImpl)
        val framesContent: Content = createContent(ui, xCoroutineThreadView)
        framesContent.isCloseable = false
        ui.addContent(framesContent, 0, PlaceInGrid.right, true)
        session.addSessionListener(xCoroutineThreadView.debugSessionListener(session))
        session.rebuildViews()
        return xCoroutineThreadView
    }

    private fun coroutinesPanelShouldBeShown() =
        alwaysShowPanel || configuration is ExternalSystemRunConfiguration || coroutineAgentAttached

    private fun createContent(ui: RunnerLayoutUi, createContentParamProvider: CreateContentParamsProvider): Content {
        val param = createContentParamProvider.createContentParams()
        return ui.createContent(param.id, param.component, param.displayName, param.icon, param.parentComponent)
    }

    override fun dispose() {
        isDisposed = true
        connection?.disconnect()
        connection = null
    }
}
