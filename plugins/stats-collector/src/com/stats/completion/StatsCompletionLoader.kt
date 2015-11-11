package com.stats.completion

import com.intellij.codeInsight.lookup.LookupAdapter
import com.intellij.codeInsight.lookup.LookupEvent
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.components.AbstractProjectComponent
import com.intellij.openapi.project.Project
import java.beans.PropertyChangeListener


class CompletionTrackerInitializer(project: Project): AbstractProjectComponent(project) {
    private val lookupPopupActionTracker = CompletionPopupActionsTracker()
    
    private val lookupTrackerInitializer = PropertyChangeListener {
        val lookup = it.newValue
        if (lookup is LookupImpl) {
            lookup.addLookupListener(TrackingLookupListener(lookupPopupActionTracker))
        }
    }

    override fun initComponent() {
        ActionManager.getInstance().addAnActionListener(lookupPopupActionTracker)
    }

    override fun disposeComponent() {
        ActionManager.getInstance().removeAnActionListener(lookupPopupActionTracker)
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
    fun typed(c: Char)
    
    class Adapter: CompletionPopupListener {
        override fun downPressed() = Unit
        override fun upPressed() = Unit
        override fun backSpacePressed() = Unit
        override fun typed(c: Char) = Unit
    }
}

class CompletionPopupActionsTracker : AnActionListener.Adapter() {
    
    private val down = ActionManager.getInstance().getAction(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN)
    private val up = ActionManager.getInstance().getAction(IdeActions.ACTION_EDITOR_MOVE_CARET_UP)
    private val backspace = ActionManager.getInstance().getAction(IdeActions.ACTION_EDITOR_BACKSPACE)
    
    var popupListener: CompletionPopupListener = CompletionPopupListener.Adapter()
    
    private fun obtainLookup(dataContext: DataContext) = LookupManager.getActiveLookup(CommonDataKeys.EDITOR.getData(dataContext)) as LookupImpl?

    override fun beforeActionPerformed(action: AnAction, dataContext: DataContext, event: AnActionEvent) {
        obtainLookup(dataContext) ?: return
        when (action) {
            down -> popupListener.downPressed()
            up -> popupListener.upPressed()
            backspace -> popupListener.backSpacePressed()
        }
    }

    override fun beforeEditorTyping(c: Char, dataContext: DataContext) {
        obtainLookup(dataContext) ?: return
        popupListener.typed(c)
    }
}

class TrackingLookupListener(private val completionTracker: CompletionPopupActionsTracker): CompletionPopupListener, LookupAdapter() {
    private var completionPopupShown = false
    
    override fun lookupCanceled(event: LookupEvent) {
        println("Cancelled")
    }

    override fun currentItemChanged(event: LookupEvent) {
        if (!completionPopupShown) {
            completionTracker.popupListener = this
            completionPopupShown = true
        }
    }

    override fun itemSelected(event: LookupEvent) {
        println("Item selected")
    }
    
    override fun downPressed() {
        println("Down pressed")
    }

    override fun upPressed() {
        println("Up pressed")
    }

    override fun backSpacePressed() {
        println("Backspace pressed")
    }

    override fun typed(c: Char) {
        println("Typed $c")
    }
    
}