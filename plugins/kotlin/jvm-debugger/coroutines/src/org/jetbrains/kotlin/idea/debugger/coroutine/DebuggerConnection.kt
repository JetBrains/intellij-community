// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.debugger.coroutine

import com.intellij.debugger.engine.JavaDebugProcess
import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.ui.RunnerLayoutUi
import com.intellij.execution.ui.layout.PlaceInGrid
import com.intellij.execution.ui.layout.impl.RunnerLayoutUiImpl
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener
import com.intellij.util.messages.MessageBusConnection
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.impl.FrontendXDebuggerManagerListener
import com.intellij.xdebugger.impl.frame.XDebugSessionProxy
import com.intellij.xdebugger.impl.util.MonolithUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.kotlin.idea.debugger.coroutine.util.CreateContentParamsProvider
import org.jetbrains.kotlin.idea.debugger.coroutine.util.logger
import org.jetbrains.kotlin.idea.debugger.coroutine.view.CoroutineView

class DebuggerConnection(
    val project: Project,
    val configuration: RunConfigurationBase<*>?,
    val params: JavaParameters?,
    shouldAttachCoroutineAgent: Boolean,
    // Used externally on the Android Studio side
    @Suppress("MemberVisibilityCanBePrivate")
    val alwaysShowPanel: Boolean = false
) : FrontendXDebuggerManagerListener, Disposable {
    companion object {
        private val log by logger
    }

    private var connection: MessageBusConnection? = null
    private val coroutineAgentAttached: Boolean
    private var isDisposed = false

    init {
        if (params is JavaParameters && shouldAttachCoroutineAgent) {
            // gradle related logic in KotlinGradleCoroutineDebugProjectResolver
            coroutineAgentAttached = CoroutineAgentConnector.attachCoroutineAgent(project, configuration, params)
        } else {
            coroutineAgentAttached = false
            log.debug("Coroutine debugger disabled.")
        }

        connection = project.messageBus.connect()
        connection?.subscribe(FrontendXDebuggerManagerListener.TOPIC, this)
    }

    override fun sessionStarted(session: XDebugSessionProxy) {
        // Coroutines view is only supported in monolith
        val monolithSession = MonolithUtils.findSessionById(session.id) ?: return
        session.coroutineScope.launch(Dispatchers.EDT) {
            session.sessionTabWhenInitialized.await()
            val debugProcess = monolithSession.debugProcess
            if (debugProcess is JavaDebugProcess &&
                !isDisposed &&
                coroutinesPanelShouldBeShown() &&
                !coroutinePanelIsRegistered(session)
            ) {
                registerXCoroutinesPanel(session, debugProcess, monolithSession)?.let {
                    Disposer.register(this@DebuggerConnection, it)
                }
            }
        }
    }

    private fun coroutinePanelIsRegistered(session: XDebugSessionProxy): Boolean {
        val ui = session.sessionTab?.ui as? RunnerLayoutUiImpl ?: return false
        val contentUi = ui.contentUI
        val content = invokeAndWaitIfNeeded {
            contentUi.findContent(CoroutineDebuggerContentInfo.XCOROUTINE_THREADS_CONTENT)
        }
        return content != null
    }

    override fun sessionStopped(session: XDebugSessionProxy) {
        ApplicationManager.getApplication().invokeLater {
            Disposer.dispose(this)
        }
    }

    private fun registerXCoroutinesPanel(
        sessionProxy: XDebugSessionProxy,
        javaDebugProcess: JavaDebugProcess,
        monolithSession: XDebugSession
    ): Disposable? {
        val ui = sessionProxy.sessionTab?.ui ?: return null
        val coroutineThreadView = CoroutineView(project, javaDebugProcess)
        val framesContent: Content = createContent(ui, coroutineThreadView)
        framesContent.isCloseable = false
        ui.addContent(framesContent, 0, PlaceInGrid.right, true)
        ui.addListener(object : ContentManagerListener {
            override fun selectionChanged(event: ContentManagerEvent) {
                val content = event.content
                if (content == framesContent && content.isSelected) {
                    val suspendContext = monolithSession.suspendContext
                    if (suspendContext is SuspendContextImpl) {
                        coroutineThreadView.renewRoot(suspendContext)
                    } else {
                        coroutineThreadView.resetRoot()
                    }
                }
            }
        }, this)
        monolithSession.addSessionListener(coroutineThreadView.debugSessionListener(monolithSession))
        sessionProxy.rebuildViews()
        return coroutineThreadView
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
