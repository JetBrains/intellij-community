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
package com.intellij.stats.completion

import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.completion.tracker.PositionTrackingListener
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ApplicationComponent
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.stats.sender.isSendAllowed
import com.intellij.stats.sender.isUnitTestMode
import com.intellij.stats.experiment.WebServiceStatus
import com.intellij.stats.personalization.UserFactorDescriptions
import com.intellij.stats.personalization.UserFactorStorage
import com.intellij.stats.personalization.UserFactorsManager
import java.beans.PropertyChangeListener


class CompletionTrackerInitializer(experimentHelper: WebServiceStatus): ApplicationComponent {
    companion object {
        var isEnabledInTests = false
    }

    private val actionListener = LookupActionsListener()

    private val lookupTrackerInitializer = PropertyChangeListener {
        val lookup = it.newValue
        if (lookup == null) {
            actionListener.listener = CompletionPopupListener.Adapter()
        }
        else if (lookup is LookupImpl) {
            if (isUnitTestMode() && !isEnabledInTests) return@PropertyChangeListener

            val globalStorage = UserFactorStorage.getInstance()
            val projectStorage = UserFactorStorage.getInstance(lookup.project)

            val userFactors = UserFactorsManager.getInstance(lookup.project).getAllFactors()
            val userFactorValues = mutableMapOf<String, String?>()
            userFactors.asSequence().map { "${it.id}:Global" to it.compute(globalStorage) }.toMap(userFactorValues)
            userFactors.asSequence().map { "${it.id}:Project" to it.compute(projectStorage) }.toMap(userFactorValues)

            lookup.putUserData(UserFactorsManager.USER_FACTORS_KEY, userFactorValues)
            val shownTimesTracker = PositionTrackingListener(lookup)
            lookup.setPrefixChangeListener(shownTimesTracker)

            UserFactorStorage.applyOnBoth(lookup.project, UserFactorDescriptions.COMPLETION_USAGE) {
                it.fireCompletionUsed()
            }

            val tracker = actionsTracker(lookup, experimentHelper)
            actionListener.listener = tracker
            lookup.addLookupListener(tracker)
            lookup.setPrefixChangeListener(tracker)

            // setPrefixChangeListener has addPrefixChangeListener semantics
            lookup.setPrefixChangeListener(TimeBetweenTypingTracker(lookup.project))
            lookup.addLookupListener(LookupCompletedTracker())
            lookup.addLookupListener(LookupStartedTracker())
        }
    }

    private fun actionsTracker(lookup: LookupImpl, experimentHelper: WebServiceStatus): CompletionActionsTracker {
        val logger = CompletionLoggerProvider.getInstance().newCompletionLogger()
        return CompletionActionsTracker(lookup, logger, experimentHelper)
    }

    private fun shouldInitialize() = isSendAllowed() || isUnitTestMode()

    override fun initComponent() {
        if (!shouldInitialize()) return

        ActionManager.getInstance().addAnActionListener(actionListener)
        ApplicationManager.getApplication().messageBus.connect().subscribe(ProjectManager.TOPIC, object : ProjectManagerListener {
            override fun projectOpened(project: Project) {
                val lookupManager = LookupManager.getInstance(project)
                lookupManager.addPropertyChangeListener(lookupTrackerInitializer)
            }

            override fun projectClosed(project: Project) {
                val lookupManager = LookupManager.getInstance(project)
                lookupManager.removePropertyChangeListener(lookupTrackerInitializer)
            }
        })
    }

    override fun disposeComponent() {
        if (!shouldInitialize()) return

        ActionManager.getInstance().removeAnActionListener(actionListener)
    }

}