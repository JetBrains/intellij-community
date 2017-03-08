package com.intellij.plugin

import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.sorting.isMlSortingEnabled
import com.intellij.sorting.setMlSortingEnabled

class ToggleMlSorting: AnAction() {

    private val enableText = "Disable ML sorting"
    private val disableText = "Enable ML sorting"

    init {
        templatePresentation.text = "Toggle ML sorting"
    }
    
    override fun update(e: AnActionEvent) {
        e.presentation.text = if (isMlSortingEnabled()) enableText else disableText
    }

    override fun actionPerformed(e: AnActionEvent) {
        val before = isMlSortingEnabled()
        setMlSortingEnabled(!before)
        
        val editor = CommonDataKeys.EDITOR.getData(e.dataContext)
        val project = CommonDataKeys.PROJECT.getData(e.dataContext)

        val content = if (before) "Reranking disabled" else "Reranking enabled"
        val collector = "Completion Stats Collector"
        val notification = Notification(collector, collector, content, NotificationType.INFORMATION)
        
        notification.notify(project)
        
        
        val lookup = LookupManager.getActiveLookup(editor) as? LookupImpl ?: return
        lookup.resort(false)
    }
    
}