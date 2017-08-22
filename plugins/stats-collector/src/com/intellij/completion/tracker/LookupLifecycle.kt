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
package com.intellij.completion.tracker

import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import java.beans.PropertyChangeListener


interface LookupLifecycleListener {
    fun lookupCreated(lookup: LookupImpl) {}
    fun lookupAbandoned() {}
}


fun LookupLifecycleListener.toPropertyChangeListener(): PropertyChangeListener {
    return PropertyChangeListener  {
        val lookup = it.newValue
        if (lookup == null) {
            lookupAbandoned()
        }
        else if (lookup is LookupImpl) {
            lookupCreated(lookup)
        }
    }
}


fun lookupLifecycleListenerInitializer(listener: LookupLifecycleListener): ProjectManagerListener {
    val listenerWrapper = listener.toPropertyChangeListener()
    return object : ProjectManagerListener {
        override fun projectOpened(project: Project) {
            val lookupManager = LookupManager.getInstance(project)
            lookupManager.addPropertyChangeListener(listenerWrapper)
        }

        override fun projectClosed(project: Project) {
            val lookupManager = LookupManager.getInstance(project)
            lookupManager.removePropertyChangeListener(listenerWrapper)
        }
    }
}


fun registerProjectManagerListener(listener: ProjectManagerListener) {
    ApplicationManager.getApplication().messageBus.connect().subscribe(ProjectManager.TOPIC, listener)
}