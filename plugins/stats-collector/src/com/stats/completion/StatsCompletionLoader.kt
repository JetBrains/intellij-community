package com.stats.completion

import com.intellij.codeInsight.lookup.LookupAdapter
import com.intellij.codeInsight.lookup.LookupEvent
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.codeInsight.lookup.impl.PrefixChangeListener
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.components.AbstractProjectComponent
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import java.beans.PropertyChangeListener


class CompletionTrackerInitializer(project: Project): AbstractProjectComponent(project) {
    private val lookupActionsTracker = LookupActionsListener()
    
    private val lookupTrackerInitializer = PropertyChangeListener {
        val lookup = it.newValue
        if (lookup is LookupImpl) {
            val logger = CompletionLoggerProvider.getInstance().newCompletionLogger()
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
    fun backSpacePressed()
    
    class Adapter: CompletionPopupListener {
        override fun downPressed() = Unit
        override fun upPressed() = Unit
        override fun backSpacePressed() = Unit
    }
}


class LookupActionsListener : AnActionListener.Adapter() {
    private val down = ActionManager.getInstance().getAction(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN)
    private val up = ActionManager.getInstance().getAction(IdeActions.ACTION_EDITOR_MOVE_CARET_UP)
    private val backspace = ActionManager.getInstance().getAction(IdeActions.ACTION_EDITOR_BACKSPACE)

    var listener: CompletionPopupListener = CompletionPopupListener.Adapter()
    
    override fun afterActionPerformed(action: AnAction, dataContext: DataContext, event: AnActionEvent) {
        when (action) {
            down -> listener.downPressed()
            up -> listener.upPressed()
            backspace -> listener.backSpacePressed()
        }
    }
}

class LookupStringWithRelevance(val item: String, val relevance: List<Pair<String, Any>>) {

    fun toData(): String {
        val builder = StringBuilder()
        with(builder, {
            append("LEN(")
            append(item.length)
            append(")")
            if (relevance.isNotEmpty()) {
                append(" RELEVANCE[")
                relevance.forEach {
                    append(it.first)
                    append('(')
                    append(it.second)
                    append(") ")
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
                               private val logger: CompletionLogger) : CompletionPopupListener, 
                                                                       PrefixChangeListener, 
                                                                       LookupAdapter() 
{

    private var completionStarted = false
    
    override fun lookupCanceled(event: LookupEvent) {
        val items = lookup.items
        val prefix = lookup.additionalPrefix

        if (items.firstOrNull()?.lookupString?.equals(prefix) ?: false) {
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
        val currentItem = lookup.currentItem
        val index = lookup.items.indexOf(currentItem)
        logger.itemSelectedCompletionFinished(index, currentItem!!.lookupString)
    }

    override fun downPressed() {
        val current = lookup.currentItem
        val index = lookup.items.indexOf(current)
        logger.downPressed(index, current!!.lookupString)
    }

    override fun upPressed() {
        val current = lookup.currentItem
        val index = lookup.items.indexOf(current)
        logger.upPressed(index, current!!.lookupString)
    }

    override fun backSpacePressed() {
        val current = lookup.currentItem
        val index = lookup.items.indexOf(current)
        logger.backspacePressed(index, current!!.lookupString, lookup.toRelevanceDataList())
    }
    
    override fun afterAppend(c: Char) {
        logger.charTyped(c, lookup.toRelevanceDataList())
    }
}