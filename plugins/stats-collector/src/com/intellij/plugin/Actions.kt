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

class ToggleManualMlSorting : AnAction() {

    private val disableText = "Disable Manual ML Sorting"
    private val enableText = "Enable Manual ML Sorting"

    init {
        templatePresentation.text = "Toggle Manual ML Sorting"
    }
    
    override fun update(e: AnActionEvent) {
        if (!ManualExperimentControl.isOn) {
            e.presentation.isEnabledAndVisible = false
            return
        }
        
        e.presentation.text = if (ManualMlSorting.isOn) disableText else enableText
    }

    override fun actionPerformed(e: AnActionEvent) {
        val before = ManualMlSorting.isOn
        ManualMlSorting.switch()
        
        val editor = CommonDataKeys.EDITOR.getData(e.dataContext)
        val project = CommonDataKeys.PROJECT.getData(e.dataContext)


        val lookup = LookupManager.getActiveLookup(editor)
        if (lookup != null) {
            CodeCompletionHandlerBase(CompletionType.BASIC).invokeCompletion(project!!, editor!!, 10, false, false)
        } else {
            val content = if (before) "Manual ML Sorting Disabled" else "Manual ML Sorting Enabled"
            val collector = "Completion Stats Collector"
            val notification = Notification(collector, collector, content, NotificationType.INFORMATION)
            notification.notify(project)
        }
    }
    
}

internal fun ApplicationProperty.switch() {
    isOn = !isOn
}

abstract class ApplicationProperty {
    internal abstract val key: String
    internal abstract val default: Boolean
    
    var isOn: Boolean 
        get() = PropertiesComponent.getInstance().getBoolean(key, default) 
        set(value) = PropertiesComponent.getInstance().setValue(key, value, default)
}   

object ManualExperimentControl : ApplicationProperty() {
    override val key = "ml.control.experiment.manually"
    override val default = false
}


object ManualMlSorting : ApplicationProperty() {
    override val key = "ml.manual.sorting"
    override val default = false
}