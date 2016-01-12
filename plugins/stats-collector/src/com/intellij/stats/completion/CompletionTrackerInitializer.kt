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
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import java.beans.PropertyChangeListener


class CompletionTrackerInitializer(project: Project): AbstractProjectComponent(project) {
    private val lookupActionsTracker = LookupActionsListener()
    
    private val lookupTrackerInitializer = PropertyChangeListener {
        val lookup = it.newValue
        if (lookup is LookupImpl) {
            val logger = CompletionLoggerProvider.Factory.getInstance().newCompletionLogger()
            val tracker = CompletionActionsTracker(lookup, logger)
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
    fun downPressed()
    fun upPressed()
    fun beforeBackspacePressed()
    fun afterBackspacePressed()
    fun beforeCharTyped(c: Char)
    
    class Adapter: CompletionPopupListener {
        override fun downPressed() = Unit
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

class LookupStringWithRelevance(val item: String, val relevance: List<Pair<String, Any>>) {

    fun toData(): String {
        val builder = StringBuilder()
        with(builder, {
            append("LEN=${item.length} ")
            if (relevance.isNotEmpty()) {
                append("RELEVANCE=[")
                var first = true
                relevance.forEach {
                    if (!first) {
                        append(", ")
                    }
                    first = false
                    append("${it.first}=${it.second}")
                }
                append("]")
            }
        })
        return builder.toString()
    }

}


fun LookupImpl.toRelevanceDataList(): List<LookupStringWithRelevance> {
    val items = items
    val relevanceMap = getRelevanceObjects(items, false)
    return items.map {
        val relevance: List<Pair<String, Any>>? = relevanceMap[it]
        LookupStringWithRelevance(it.lookupString, relevance ?: emptyList())
    }
}



class CompletionActionsTracker(private val lookup: LookupImpl,
                               private val logger: CompletionLogger) 
      : CompletionPopupListener, 
        PrefixChangeListener, 
        LookupAdapter() {

    private var completionStarted = false
    private var selectedByDotTyping = false
    
    private fun isCompletionActive(): Boolean {
        return completionStarted && !lookup.isLookupDisposed
                || ApplicationManager.getApplication().isUnitTestMode
    } 
    
    override fun lookupCanceled(event: LookupEvent) {
        if (!completionStarted) return
        
        val items = lookup.items
        if (lookup.currentItem == null) {
            logger.completionCancelled()
            return
        }
        
        val prefix = lookup.itemPattern(lookup.currentItem!!)
        val wasTyped = items.firstOrNull()?.lookupString?.equals(prefix) ?: false
        if (wasTyped || selectedByDotTyping) {
            logger.itemSelectedByTyping(prefix)
        }
        else {
            logger.completionCancelled()
        }
    }

    override fun currentItemChanged(event: LookupEvent) {
        if (!completionStarted) {
            completionStarted = true
            logger.completionStarted(lookup.toRelevanceDataList())
        }
    }
    
    override fun itemSelected(event: LookupEvent) {
        if (!completionStarted) return
        
        val currentItem = lookup.currentItem
        val index = lookup.items.indexOf(currentItem)
        logger.itemSelectedCompletionFinished(index, currentItem?.lookupString ?: "NULL", lookup.toRelevanceDataList())
    }

    override fun downPressed() {
        if (!isCompletionActive()) return
        
        val current = lookup.currentItem
        val index = lookup.items.indexOf(current)
        logger.downPressed(index, current!!.lookupString, lookup.toRelevanceDataList())
    }

    override fun upPressed() {
        if (!isCompletionActive()) return
        
        val current = lookup.currentItem
        val index = lookup.items.indexOf(current)
        logger.upPressed(index, current!!.lookupString, lookup.toRelevanceDataList())
    }

    override fun beforeBackspacePressed() {
        if (!isCompletionActive()) return
        logger.beforeBackspacePressed(lookup.toRelevanceDataList())
    }

    override fun afterBackspacePressed() {
        if (!isCompletionActive()) return
        
        val current = lookup.currentItem
        val index = lookup.items.indexOf(current)
        
        logger.afterBackspacePressed(index, current?.lookupString ?: "NULL", lookup.toRelevanceDataList())
    }
    
    override fun afterAppend(c: Char) {
        if (!isCompletionActive()) return
        
        logger.afterCharTyped(c, lookup.toRelevanceDataList())
    }

    override fun beforeCharTyped(c: Char) {
        if (!isCompletionActive()) return
        if (c == '.') {
            val item = lookup.currentItem!!
            val text = lookup.itemPattern(item)
            if (item.lookupString.equals(text)) {
                selectedByDotTyping = true
            }
        }
        else {
            logger.beforeCharTyped(c, lookup.toRelevanceDataList())
        }
    }
    
}