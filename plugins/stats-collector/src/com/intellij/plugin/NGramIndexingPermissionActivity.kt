/*
 * Copyright 2000-2018 JetBrains s.r.o.
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
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.stats.ngram.NGramFileBasedIndex

/**
 * @author Vitaliy.Bibaev
 */
class NGramIndexingPermissionActivity : StartupActivity {

  private companion object {
    const val DO_NOT_ASK_AGAIN_KEY = "com.intellij.plugin.completion.ngram.indexing.not.ask"
    const val ANSWERED = "com.intellij.plugin.completion.ngram.indexing.answered"
    const val PLUGIN_NAME = "Completion Stats Collector"
    private const val MESSAGE_TEXT = """We’re going to use ngram frequencies to rank completion items better. This
        can increase the indexing time by ~20-50% on large projects (especially on MS Windows). Will you allow us
        to perform this experiment on your project?
    """
  }

  override fun runActivity(project: Project) {
    if (needToAsk(project)) {
      notify(project)
    }
  }

  private fun notify(project: Project) {
    Notification(PLUGIN_NAME, PLUGIN_NAME, MESSAGE_TEXT, NotificationType.INFORMATION)
        .addAction(object : NotificationAction("Allow") {
          override fun actionPerformed(event: AnActionEvent, notification: Notification) {
            NGramIndexingProperty.setEnabled(project, true)
            NGramFileBasedIndex.requestRebuild()
            answered(project, notification)
          }
        })
        .addAction(object : NotificationAction("Deny") {
          override fun actionPerformed(event: AnActionEvent, notification: Notification) {
            NGramIndexingProperty.setEnabled(project, false)
            answered(project, notification)
          }
        })
        .addAction(object : NotificationAction("Do not ask again") {
          override fun actionPerformed(event: AnActionEvent, notification: Notification) {
            NGramIndexingProperty.setEnabled(project, false)
            PropertiesComponent.getInstance().setValue(DO_NOT_ASK_AGAIN_KEY, true)
            answered(project, notification)
          }
        })
        .notify(project)
  }

  private fun answered(project: Project, notification: Notification) {
    PropertiesComponent.getInstance(project).setValue(ANSWERED, true)
    notification.expire()
  }

  private fun needToAsk(project: Project): Boolean {
    return !PropertiesComponent.getInstance().getBoolean(DO_NOT_ASK_AGAIN_KEY, false) &&
        !PropertiesComponent.getInstance(project).getBoolean(ANSWERED, false)
  }
}