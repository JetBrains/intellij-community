package com.intellij.stats.completion

import com.intellij.codeInsight.lookup.LookupAdapter
import com.intellij.codeInsight.lookup.LookupEvent
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.codeInsight.lookup.impl.PrefixChangeListener
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.AbstractProjectComponent
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.stats.completion.experiment.StatusInfoProvider
import java.beans.PropertyChangeListener


class CompletionTrackerInitializer(project: Project, experimentHelper: StatusInfoProvider): AbstractProjectComponent(project) {
    private val lookupActionsTracker = LookupActionsListener()
    
    private val lookupTrackerInitializer = PropertyChangeListener {
        val lookup = it.newValue
        if (lookup == null) {
            lookupActionsTracker.listener = CompletionPopupListener.Adapter()
        }
        else if (lookup is LookupImpl) {
            val logger = CompletionLoggerProvider.getInstance().newCompletionLogger()
            val tracker = CompletionActionsTracker(lookup, logger, experimentHelper)
            lookupActionsTracker.listener = tracker
            lookup.addLookupListener(tracker)
            lookup.setPrefixChangeListener(tracker)
        }
    }

    override fun initComponent() {
        ActionManager.getInstance().addAnActionListener(lookupActionsTracker)
    }

    override fun disposeComponent() {
        ActionManager.getInstance().removeAnActionListener(lookupActionsTracker)
        CompletionLoggerProvider.getInstance().dispose()
    }

    override fun projectOpened() {
        val manager = LookupManager.getInstance(myProject)
        manager.addPropertyChangeListener(lookupTrackerInitializer)
    }
    
    override fun projectClosed() {
        val manager = LookupManager.getInstance(myProject)
        manager.removePropertyChangeListener(lookupTrackerInitializer)
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
                               private val experimentHelper: StatusInfoProvider) 
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