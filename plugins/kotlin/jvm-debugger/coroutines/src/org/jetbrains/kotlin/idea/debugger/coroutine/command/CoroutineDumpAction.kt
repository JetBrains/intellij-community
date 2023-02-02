// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.debugger.coroutine.command

import com.intellij.debugger.DebuggerManagerEx
import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.debugger.engine.events.SuspendContextCommandImpl
import com.intellij.debugger.impl.DebuggerUtilsEx
import com.intellij.execution.filters.ExceptionFilters
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.ui.RunnerLayoutUi
import com.intellij.execution.ui.layout.impl.RunnerContentUi
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.util.Disposer
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.text.DateFormatUtil
import com.intellij.xdebugger.impl.XDebuggerManagerImpl
import org.jetbrains.kotlin.idea.debugger.coroutine.KotlinDebuggerCoroutinesBundle
import org.jetbrains.kotlin.idea.debugger.coroutine.data.CompleteCoroutineInfoData
import org.jetbrains.kotlin.idea.debugger.coroutine.data.toCompleteCoroutineInfoData
import org.jetbrains.kotlin.idea.debugger.coroutine.proxy.CoroutineDebugProbesProxy
import org.jetbrains.kotlin.idea.debugger.coroutine.util.logger
import org.jetbrains.kotlin.idea.debugger.coroutine.view.CoroutineDumpPanel

class CoroutineDumpAction : AnAction() {
    companion object {
        val log by logger
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val context = DebuggerManagerEx.getInstanceEx(project).context
        val session = context.debuggerSession
        if (session != null && session.isAttached) {
            val process = context.debugProcess ?: return
            process.managerThread.schedule(object : SuspendContextCommandImpl(context.suspendContext) {
                override fun contextAction(suspendContext: SuspendContextImpl) {
                    val states = CoroutineDebugProbesProxy(suspendContext).dumpCoroutines()
                    if (states.isOk()) {
                        val coroutines = states.cache.map { it.toCompleteCoroutineInfoData() }
                        ApplicationManager.getApplication().invokeLater({
                            val ui = session.xDebugSession?.ui
                            if (ui == null) {
                                log.warn("Failed to retrieve UI instance")
                                createCoroutineDumpFailedErrorNotification(project)
                                return@invokeLater
                            }

                            addCoroutineDump(project, coroutines, ui, session.searchScope)
                        }, ModalityState.NON_MODAL)
                    } else {
                        createCoroutineDumpFailedErrorNotification(project)
                    }
                }
            })
        }
    }

    fun createCoroutineDumpFailedErrorNotification(project: Project) =
        XDebuggerManagerImpl.getNotificationGroup().createNotification(
            KotlinDebuggerCoroutinesBundle.message("coroutine.dump.failed"),
            MessageType.ERROR
        ).notify(project)

    /**
     * Analog of [DebuggerUtilsEx.addThreadDump].
     */
    fun addCoroutineDump(project: Project, coroutines: List<CompleteCoroutineInfoData>, ui: RunnerLayoutUi, searchScope: GlobalSearchScope) {
        val consoleBuilder = TextConsoleBuilderFactory.getInstance().createBuilder(project)
        consoleBuilder.filters(ExceptionFilters.getFilters(searchScope))
        val consoleView = consoleBuilder.console
        val toolbarActions = DefaultActionGroup()
        consoleView.allowHeavyFilters()
        val panel = CoroutineDumpPanel(project, consoleView, toolbarActions, coroutines)

        @Suppress("HardCodedStringLiteral")
        val id = "DumpKt " + DateFormatUtil.formatTimeWithSeconds(System.currentTimeMillis())
        val content = ui.createContent(id, panel, id, null, null).apply {
            putUserData(RunnerContentUi.LIGHTWEIGHT_CONTENT_MARKER, true)
            isCloseable = true
            description = KotlinDebuggerCoroutinesBundle.message("coroutine.dump.panel.title")
        }
        ui.addContent(content)
        ui.selectAndFocus(content, true, true)
        Disposer.register(content, consoleView)
    }

    override fun getActionUpdateThread() = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        val presentation = e.presentation
        val project = e.project ?: run {
            presentation.isEnabledAndVisible = false
            return
        }
        // cannot be called when no SuspendContext
        if (DebuggerManagerEx.getInstanceEx(project).context.suspendContext == null) {
            presentation.isEnabled = false
            return
        }
        val debuggerSession = DebuggerManagerEx.getInstanceEx(project).context.debuggerSession
        presentation.isEnabled = debuggerSession != null && debuggerSession.isAttached
        presentation.isVisible = presentation.isEnabled
    }
}
