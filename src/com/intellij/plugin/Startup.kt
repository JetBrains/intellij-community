package com.intellij.plugin

import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity

class NotificationManager : StartupActivity {

    companion object {
        private val PLUGIN_NAME = "Completion Stats Collector"
        private val MESSAGE_TEXT = 
                "Data about your code completion usage will be anonymously reported. " +
                "Note, the plugin doesn’t send any of your personal data or code. " + 
                "All we send is just numbers calculated on your completion usage patterns"
        
        private val MESSAGE_SHOWN_KEY = "completion.stats.allow.message.shown"
    }

    override fun runActivity(project: Project) {
        val isMessageShown = PropertiesComponent.getInstance().getBoolean(MESSAGE_SHOWN_KEY, false)
        if (!isMessageShown) notify(project)
    }
    
    private fun notify(project: Project) {
        val notification = Notification(PLUGIN_NAME, PLUGIN_NAME, MESSAGE_TEXT, NotificationType.INFORMATION)
        notification.notify(project)
        PropertiesComponent.getInstance().setValue(MESSAGE_SHOWN_KEY, true)
    }

}