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

import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.reporting.isSendAllowed

class NotificationManager : StartupActivity {

    companion object {
        private const val PLUGIN_NAME = "Completion Stats Collector"
        private const val MESSAGE_TEXT =
                "Data about your code completion usage will be anonymously reported. " +
                "No personal data or code will be sent."

        private const val MESSAGE_TEXT_EAP = "$MESSAGE_TEXT This is only enabled in EAP builds."

        private const val MESSAGE_SHOWN_KEY = "completion.stats.allow.message.shown"
    }
    
    private fun isMessageShown() = PropertiesComponent.getInstance().getBoolean(MESSAGE_SHOWN_KEY, false)

    private fun setMessageShown(value: Boolean) = PropertiesComponent.getInstance().setValue(MESSAGE_SHOWN_KEY, value)
    
    override fun runActivity(project: Project) {
        // Show message in EAP build or if additional plugin installed
        if (!isMessageShown() && isSendAllowed()) {
            notify(project)
            setMessageShown(true)
        }
    }
    
    private fun notify(project: Project) {
        val messageText = if (ApplicationManager.getApplication().isEAP) MESSAGE_TEXT else MESSAGE_TEXT_EAP
        val notification = Notification(PLUGIN_NAME, PLUGIN_NAME, messageText, NotificationType.INFORMATION)
        notification.notify(project)
    }

}