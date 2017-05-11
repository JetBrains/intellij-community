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
import com.intellij.stats.completion.experiment.WebServiceStatusProvider
import com.intellij.stats.completion.experiment.isPerformExperiment
import java.beans.PropertyChangeListener


class CompletionTrackerInitializer(experimentHelper: WebServiceStatusProvider): ApplicationComponent {
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
            if (ApplicationManager.getApplication().isUnitTestMode && !isEnabledInTests) return@PropertyChangeListener

            val logger = CompletionLoggerProvider.getInstance().newCompletionLogger()
            val tracker = CompletionActionsTracker(lookup, logger, experimentHelper)
            actionListener.listener = tracker
            lookup.addLookupListener(tracker)
            lookup.setPrefixChangeListener(tracker)
        }
    }

    override fun initComponent() {
        if (!ApplicationManager.getApplication().isUnitTestMode) return

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
        if (!ApplicationManager.getApplication().isUnitTestMode) return

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

class CompletionActionsTracker(private val lookup: LookupImpl,
                               private val logger: CompletionLogger,
                               private val experimentHelper: WebServiceStatusProvider)
      : CompletionPopupListener, 
        PrefixChangeListener, 
        LookupAdapter() {
    
    companion object {
        private val DO_NOTHING: () -> Unit = { }
    }
    
    private var completionStarted = false
    private var selectedByDotTyping = false

    private var lastAction: () -> Unit = DO_NOTHING
    
    private fun isCompletionActive(): Boolean {
        return completionStarted && !lookup.isLookupDisposed
                || ApplicationManager.getApplication().isUnitTestMode
    }
    
    override fun lookupCanceled(event: LookupEvent) {
        if (!completionStarted) return

        val items = lookup.items
        if (lookup.currentItem == null) {
            lastAction = DO_NOTHING
            logger.completionCancelled()
            return
        }
        
        val prefix = lookup.itemPattern(lookup.currentItem!!)
        val wasTyped = items.firstOrNull()?.lookupString?.equals(prefix) ?: false
        if (wasTyped || selectedByDotTyping) {
            logLastAction()
            logger.itemSelectedByTyping(lookup)
        }
        else {
            lastAction = DO_NOTHING
            logger.completionCancelled()
        }
    }

    fun logLastAction() {
        lastAction()
        lastAction = DO_NOTHING
    }

    fun setLastAction(block: () -> Unit) {
        lastAction = block
    }
    
    override fun currentItemChanged(event: LookupEvent) {
        if (completionStarted) {
            return
        }

        completionStarted = true
        lastAction = {
            //this is not robust -> since at the moment of completion here could be another values
            //real approach would be to somehow detect if completion list is reordered 
            val isPerformExperiment = experimentHelper.isPerformExperiment()
            val experimentVersion = experimentHelper.getExperimentVersion()
            logger.completionStarted(lookup, isPerformExperiment, experimentVersion)
        }
    }

    override fun itemSelected(event: LookupEvent) {
        if (!completionStarted) return
        
        logLastAction()
        logger.itemSelectedCompletionFinished(lookup)
    }

    override fun beforeDownPressed() {
        logLastAction()
    }

    override fun downPressed() {
        if (!isCompletionActive()) return

        logLastAction()
        setLastAction {
            logger.downPressed(lookup)
        }
    }

    override fun beforeUpPressed() {
        logLastAction()
    }

    override fun upPressed() {
        if (!isCompletionActive()) return

        logLastAction()
        setLastAction {
            logger.upPressed(lookup)
        }
    }

    override fun beforeBackspacePressed() {
        if (!isCompletionActive()) return
        logLastAction()
    }

    override fun afterBackspacePressed() {
        if (!isCompletionActive()) return

        logLastAction()
        setLastAction {
            logger.afterBackspacePressed(lookup)
        }
    }

    override fun beforeCharTyped(c: Char) {
        if (!isCompletionActive()) return

        logLastAction()
        
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

        logLastAction()
        setLastAction {
            logger.afterCharTyped(c, lookup)
        }
    }
}