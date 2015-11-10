package com.stats.completion

import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.codeInsight.lookup.impl.actions.ChooseItemAction
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.components.ApplicationComponent


class CompletionPopupActionsTracker : AnActionListener {
    
    private val down = ActionManager.getInstance().getAction(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN)
    private val up = ActionManager.getInstance().getAction(IdeActions.ACTION_EDITOR_MOVE_CARET_UP)
    private val backspace = ActionManager.getInstance().getAction(IdeActions.ACTION_EDITOR_BACKSPACE)
    
    private fun obtainLookup(dataContext: DataContext) = LookupManager.getActiveLookup(CommonDataKeys.EDITOR.getData(dataContext)) as LookupImpl?

    override fun beforeActionPerformed(action: AnAction, dataContext: DataContext, event: AnActionEvent) {
        val lookup = obtainLookup(dataContext) ?: return
        when (action) {
            down -> println("Down")
            up -> println("Up")
            backspace -> println("Backspace")
            is ChooseItemAction -> println("Item chosen") 
        }
    }

    override fun beforeEditorTyping(c: Char, dataContext: DataContext) {
        val lookup = obtainLookup(dataContext) ?: return
        println("Typed $c")
    }

    override fun afterActionPerformed(action: AnAction, dataContext: DataContext, event: AnActionEvent) {
        val lookup = obtainLookup(dataContext) ?: return
    }
}

class CompletionStatsRetriever: ApplicationComponent.Adapter() {
    private val trackingListener = CompletionPopupActionsTracker()

    override fun initComponent() {
        ActionManager.getInstance().addAnActionListener(trackingListener)
    }
    
    override fun disposeComponent() {
        ActionManager.getInstance().removeAnActionListener(trackingListener)
    }
}