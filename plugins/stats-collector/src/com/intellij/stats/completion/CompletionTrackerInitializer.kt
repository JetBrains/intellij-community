/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.stats.completion

import com.intellij.codeInsight.lookup.LookupAdapter
import com.intellij.codeInsight.lookup.LookupEvent
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.codeInsight.lookup.impl.PrefixChangeListener
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ApplicationComponent
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.reporting.isSendAllowed
import com.intellij.reporting.isUnitTestMode
import com.intellij.stats.completion.experiment.WebServiceStatus
import java.beans.PropertyChangeListener


class CompletionTrackerInitializer(experimentHelper: WebServiceStatus): ApplicationComponent {
    companion object {
        var isEnabledInTests = false
    }

    private val actionListener = LookupActionsListener()
    
    private val lookupTrackerInitializer = PropertyChangeListener {
        val lookup = it.newValue
        if (lookup == null) {
            actionListener.listener = CompletionPopupListener.Adapter()
        }
        else if (lookup is LookupImpl) {
            if (isUnitTestMode() && !isEnabledInTests) return@PropertyChangeListener

            val logger = CompletionLoggerProvider.getInstance().newCompletionLogger()
            val tracker = CompletionActionsTracker(lookup, logger, experimentHelper)
            actionListener.listener = tracker
            lookup.addLookupListener(tracker)
            lookup.setPrefixChangeListener(tracker)
        }
    }

    private fun shouldInitialize() = isSendAllowed() || isUnitTestMode()

    override fun initComponent() {
        if (!shouldInitialize()) return

        ActionManager.getInstance().addAnActionListener(actionListener)
        ApplicationManager.getApplication().messageBus.connect().subscribe(ProjectManager.TOPIC, object : ProjectManagerListener {
            override fun projectOpened(project: Project) {
                val lookupManager = LookupManager.getInstance(project)
                lookupManager.addPropertyChangeListener(lookupTrackerInitializer)
            }

            override fun projectClosed(project: Project) {
                val lookupManager = LookupManager.getInstance(project)
                lookupManager.removePropertyChangeListener(lookupTrackerInitializer)
            }
        })
    }

    override fun disposeComponent() {
        if (!shouldInitialize()) return

        ActionManager.getInstance().removeAnActionListener(actionListener)
    }

}


interface CompletionPopupListener {
    fun beforeDownPressed()
    fun downPressed()
    fun beforeUpPressed()
    fun upPressed()
    fun beforeBackspacePressed()
    fun afterBackspacePressed()
    fun beforeCharTyped(c: Char)
    
    class Adapter: CompletionPopupListener {
        override fun beforeDownPressed() = Unit
        override fun downPressed() = Unit
        override fun beforeUpPressed() = Unit
        override fun upPressed() = Unit
        override fun afterBackspacePressed() = Unit
        override fun beforeBackspacePressed() = Unit
        override fun beforeCharTyped(c: Char) = Unit
    }
}


class LookupActionsListener : AnActionListener.Adapter() {
    private val LOG = Logger.getInstance(LookupActionsListener::class.java)
    private val down = ActionManager.getInstance().getAction(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN)
    private val up = ActionManager.getInstance().getAction(IdeActions.ACTION_EDITOR_MOVE_CARET_UP)
    private val backspace = ActionManager.getInstance().getAction(IdeActions.ACTION_EDITOR_BACKSPACE)

    var listener: CompletionPopupListener = CompletionPopupListener.Adapter()

    private fun logThrowables(block: () -> Unit) {
        try {
            block()
        } catch (e: Throwable) {
            logIfNotControlFlow(e)
        }
    }

    private fun logIfNotControlFlow(e: Throwable) {
        if (e is ControlFlowException) {
            throw e
        } else {
            LOG.error(e)
        }
    }

    override fun afterActionPerformed(action: AnAction, dataContext: DataContext, event: AnActionEvent?) {
        logThrowables {
            when (action) {
                down -> listener.downPressed()
                up -> listener.upPressed()
                backspace -> listener.afterBackspacePressed()
            }
        }
    }

    override fun beforeActionPerformed(action: AnAction?, dataContext: DataContext?, event: AnActionEvent?) {
        logThrowables {
            when (action) {
                down -> listener.beforeDownPressed()
                up -> listener.beforeUpPressed()
                backspace -> listener.beforeBackspacePressed()
            }
        }
    }

    override fun beforeEditorTyping(c: Char, dataContext: DataContext) {
        logThrowables {
            listener.beforeCharTyped(c)
        }
    }
}



class DeferredLog {

    companion object {
        private val DO_NOTHING: () -> Unit = { }
    }

    private var lastAction: () -> Unit = DO_NOTHING

    fun clear() {
        lastAction = DO_NOTHING
    }

    fun defer(action: () -> Unit) {
        lastAction = action
    }

    fun log() {
        lastAction()
        clear()
    }

}


class CompletionActionsTracker(private val lookup: LookupImpl,
                               private val logger: CompletionLogger,
                               private val experimentHelper: WebServiceStatus)
      : CompletionPopupListener, 
        PrefixChangeListener, 
        LookupAdapter() {

    private var completionStarted = false
    private var selectedByDotTyping = false

    private val deferredLog = DeferredLog()
    
    private fun isCompletionActive(): Boolean {
        return completionStarted && !lookup.isLookupDisposed
                || ApplicationManager.getApplication().isUnitTestMode
    }
    
    override fun lookupCanceled(event: LookupEvent) {
        if (!completionStarted) return

        val items = lookup.items
        if (lookup.currentItem == null) {
            deferredLog.clear()
            logger.completionCancelled()
            return
        }
        
        val prefix = lookup.itemPattern(lookup.currentItem!!)
        val wasTyped = items.firstOrNull()?.lookupString?.equals(prefix) ?: false
        if (wasTyped || selectedByDotTyping) {
            deferredLog.log()
            logger.itemSelectedByTyping(lookup)
        }
        else {
            deferredLog.clear()
            logger.completionCancelled()
        }
    }

    override fun currentItemChanged(event: LookupEvent) {
        if (completionStarted) {
            return
        }

        completionStarted = true
        deferredLog.defer {
            val isPerformExperiment = experimentHelper.isExperimentOnCurrentIDE()
            val experimentVersion = experimentHelper.experimentVersion()
            logger.completionStarted(lookup, isPerformExperiment, experimentVersion)
        }
    }

    override fun itemSelected(event: LookupEvent) {
        if (!completionStarted) return
        
        deferredLog.log()
        logger.itemSelectedCompletionFinished(lookup)
    }

    override fun beforeDownPressed() {
        deferredLog.log()
    }

    override fun downPressed() {
        if (!isCompletionActive()) return

        deferredLog.log()
        deferredLog.defer {
            logger.downPressed(lookup)
        }
    }

    override fun beforeUpPressed() {
        deferredLog.log()
    }

    override fun upPressed() {
        if (!isCompletionActive()) return

        deferredLog.log()
        deferredLog.defer {
            logger.upPressed(lookup)
        }
    }

    override fun beforeBackspacePressed() {
        if (!isCompletionActive()) return
        deferredLog.log()
    }

    override fun afterBackspacePressed() {
        if (!isCompletionActive()) return

        deferredLog.log()
        deferredLog.defer {
            logger.afterBackspacePressed(lookup)
        }
    }

    override fun beforeCharTyped(c: Char) {
        if (!isCompletionActive()) return

        deferredLog.log()

        if (c == '.') {
            val item = lookup.currentItem
            if (item == null) {
                logger.customMessage("Before typed $c lookup.currentItem is null; lookup size: ${lookup.items.size}")
                return
            }
            val text = lookup.itemPattern(item)
            if (item.lookupString == text) {
                selectedByDotTyping = true
            }
        }
    }

    
    override fun afterAppend(c: Char) {
        if (!isCompletionActive()) return

        deferredLog.log()
        deferredLog.defer {
            logger.afterCharTyped(c, lookup)
        }
    }
}


fun LookupImpl.prefixLength(): Int {
    val lookupOriginalStart = this.lookupOriginalStart
    val caretOffset = this.editor.caretModel.offset
    return if (lookupOriginalStart < 0) 0 else caretOffset - lookupOriginalStart + 1
}