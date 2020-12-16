package com.intellij.usage.statistics.toolwindow

import com.intellij.concurrency.JobScheduler
import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.internal.statistic.eventLog.validator.rules.impl.TestModeValidationRule
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit


class GroupsDescriptionsDownloadActivity : StartupActivity.Background {
  private var updateFuture: ScheduledFuture<*>? = null
  override fun runActivity(project: Project) {
    if (updateFuture == null && TestModeValidationRule.isTestModeEnabled()) {
      updateFuture = JobScheduler.getScheduler().scheduleWithFixedDelay(Runnable {
        GroupsDescriptionsHolder.getInstance().updateAutoSelectedFields()
      }, 0, 1, TimeUnit.DAYS)
    }

    ApplicationManager.getApplication().messageBus.connect().subscribe(DynamicPluginListener.TOPIC, object : DynamicPluginListener {
      override fun beforePluginUnload(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
        val scheduledFuture = updateFuture
        if (PLUGIN_ID == pluginDescriptor.pluginId?.idString && scheduledFuture != null) {
          scheduledFuture.cancel(true)
        }
      }
    })
  }

  companion object {
    private const val PLUGIN_ID = "org.jetbrains.usage.statistics"
  }
}