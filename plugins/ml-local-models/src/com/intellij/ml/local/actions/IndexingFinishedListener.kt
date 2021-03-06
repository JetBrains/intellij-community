package com.intellij.ml.local.actions

import com.intellij.ide.util.PropertiesComponent
import com.intellij.lang.Language
import com.intellij.ml.local.MlLocalModelsBundle
import com.intellij.ml.local.models.LocalModelsTraining
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModuleTypeId
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.indexing.diagnostic.IndexDiagnosticDumper
import com.intellij.util.indexing.diagnostic.ProjectIndexingHistory

internal class IndexingFinishedListener : IndexDiagnosticDumper.ProjectIndexingHistoryListener {
  companion object {
    private const val NOTIFICATION_EXPIRED_KEY = "ml.local.models.training.notification.expired"
    private const val SHOW_NOTIFICATION_REGISTRY = "ml.local.models.show.notification"
  }

  override fun onFinishedIndexing(projectIndexingHistory: ProjectIndexingHistory) {
    if (Registry.`is`(SHOW_NOTIFICATION_REGISTRY, false)) {
      val project = projectIndexingHistory.project
      val language = Language.findLanguageByID("JAVA") ?: return
      val isJavaProject = ModuleManager.getInstance(project).modules.any {
        it.moduleTypeName == ModuleTypeId.JAVA_MODULE
      }
      if (isJavaProject && !PropertiesComponent.getInstance(project).getBoolean(NOTIFICATION_EXPIRED_KEY)) {
        TrainingNotification(project, language).notify(project)
      }
    }
  }

  private class TrainingNotification(project: Project, language: Language) : Notification(
    MlLocalModelsBundle.message("ml.local.models.notification.groupId"),
    MlLocalModelsBundle.message("ml.local.models.notification.title"),
    MlLocalModelsBundle.message("ml.local.models.notification.content"),
    NotificationType.INFORMATION
  ) {
    init {
      addAction(object : NotificationAction(MlLocalModelsBundle.message("ml.local.models.notification.ok")) {
        override fun actionPerformed(e: AnActionEvent, notification: Notification) {
          notificationExpired(project)
          LocalModelsTraining.train(project, language)
          notification.expire()
        }
      })
      addAction(object : NotificationAction(MlLocalModelsBundle.message("ml.local.models.notification.cancel")) {
        override fun actionPerformed(e: AnActionEvent, notification: Notification) {
          notificationExpired(project)
          notification.expire()
        }
      })
    }

    private fun notificationExpired(project: Project) {
      PropertiesComponent.getInstance(project).setValue(NOTIFICATION_EXPIRED_KEY, true)
    }
  }
}