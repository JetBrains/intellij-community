package com.intellij.plugin

import com.intellij.codeInsight.completion.CodeCompletionHandlerBase
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys

class ToggleMlSorting: AnAction() {

    private val enableText = "Disable ML sorting"
    private val disableText = "Enable ML sorting"

    init {
        templatePresentation.text = "Toggle ML sorting"
    }
    
    override fun update(e: AnActionEvent) {
        e.presentation.text = if (isMlSortingEnabledByForce()) enableText else disableText
    }

    override fun actionPerformed(e: AnActionEvent) {
        val before = isMlSortingEnabledByForce()
        setMlSortingEnabledByForce(!before)
        
        val editor = CommonDataKeys.EDITOR.getData(e.dataContext)
        val project = CommonDataKeys.PROJECT.getData(e.dataContext)


        val lookup = LookupManager.getActiveLookup(editor)
        if (lookup != null) {
            CodeCompletionHandlerBase(CompletionType.BASIC).invokeCompletion(project!!, editor!!, 10, false, false)
        } else {
            val content = if (before) "Reranking disabled" else "Reranking enabled"
            val collector = "Completion Stats Collector"
            val notification = Notification(collector, collector, content, NotificationType.INFORMATION)
            notification.notify(project)
        }
    }
    
}


fun isMlSortingEnabledByForce(): Boolean = PropertiesComponent.getInstance().getBoolean("ml.sorting.forced", false)

fun setMlSortingEnabledByForce(value: Boolean) = PropertiesComponent.getInstance().setValue("ml.sorting.forced", value, false)