package com.stats.completion

import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupAdapter
import com.intellij.codeInsight.lookup.LookupEvent
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.components.AbstractProjectComponent
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import java.beans.PropertyChangeListener


class CompletionTrackerInitializer(project: Project): AbstractProjectComponent(project) {
    private val lookupPopupActionTracker = LookupActionsListener()
    
    private val lookupTrackerInitializer = PropertyChangeListener {
        val lookup = it.newValue
        if (lookup is LookupImpl) {
            val logger = CompletionLoggerProvider.getInstance().newCompletionLogger()
            lookup.addLookupListener(CompletionActionsTracker(lookupPopupActionTracker, logger))
        }
    }

    override fun initComponent() {
        ActionManager.getInstance().addAnActionListener(lookupPopupActionTracker)
    }

    override fun disposeComponent() {
        ActionManager.getInstance().removeAnActionListener(lookupPopupActionTracker)
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
    fun downPressed(lookup: Lookup)
    fun upPressed(lookup: Lookup)
    fun backSpacePressed(lookup: Lookup)
    fun typed(c: Char, lookup: Lookup)
    
    class Adapter: CompletionPopupListener {
        override fun downPressed(lookup: Lookup) = Unit
        override fun upPressed(lookup: Lookup) = Unit
        override fun backSpacePressed(lookup: Lookup) = Unit
        override fun typed(c: Char, lookup: Lookup) = Unit
    }
}


class LookupActionsListener : AnActionListener.Adapter() {
    
    private val down = ActionManager.getInstance().getAction(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN)
    private val up = ActionManager.getInstance().getAction(IdeActions.ACTION_EDITOR_MOVE_CARET_UP)
    private val backspace = ActionManager.getInstance().getAction(IdeActions.ACTION_EDITOR_BACKSPACE)
    
    var popupListener: CompletionPopupListener = CompletionPopupListener.Adapter()
    
    private fun obtainLookup(dataContext: DataContext) = LookupManager.getActiveLookup(CommonDataKeys.EDITOR.getData(dataContext)) as LookupImpl?

    override fun afterActionPerformed(action: AnAction, dataContext: DataContext, event: AnActionEvent) {
        val lookup = obtainLookup(dataContext) ?: return
        when (action) {
            down -> popupListener.downPressed(lookup)
            up -> popupListener.upPressed(lookup)
            backspace -> popupListener.backSpacePressed(lookup)
        }
    }

    override fun beforeEditorTyping(c: Char, dataContext: DataContext) {
        val lookup = obtainLookup(dataContext) ?: return
        popupListener.typed(c, lookup)
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


class CompletionActionsTracker(private val completionListener: LookupActionsListener,
                               private val logger: CompletionLogger) : CompletionPopupListener, LookupAdapter() {
    
    override fun lookupCanceled(event: LookupEvent) {
        val lookup = event.lookup as LookupImpl
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
        if (completionListener.popupListener != this) {
            completionListener.popupListener = this
            val lookup = event.lookup as LookupImpl
            logger.completionStarted(lookup.toRelevanceDataList())
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
    
    override fun itemSelected(event: LookupEvent) {
        val currentItem = event.lookup.currentItem
        val index = event.lookup.items.indexOf(currentItem)
        logger.itemSelectedCompletionFinished(index, currentItem!!.lookupString)
    }

    override fun downPressed(lookup: Lookup) {
        val current = lookup.currentItem
        val index = lookup.items.indexOf(current)
        logger.downPressed(index, current!!.lookupString)
    }

    override fun upPressed(lookup: Lookup) {
        val current = lookup.currentItem
        val index = lookup.items.indexOf(current)
        logger.upPressed(index, current!!.lookupString)
    }

    override fun backSpacePressed(lookup: Lookup) {
        val lookupImpl = lookup as LookupImpl
        val current = lookup.currentItem
        val index = lookup.items.indexOf(current)
        logger.backspacePressed(index, current!!.lookupString, lookupImpl.toRelevanceDataList())
    }

    override fun typed(c: Char, lookup: Lookup) {
        val lookupImpl = lookup as LookupImpl
        logger.charTyped(c, lookupImpl.toRelevanceDataList())
    }
    
}