// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.ml.local

import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupManagerListener
import com.intellij.ide.util.PropertiesComponent
import com.intellij.lang.Language
import com.intellij.lang.java.JavaLanguage
import com.intellij.ml.local.MlLocalModelsBundle
import com.intellij.ml.local.models.LocalModelsTraining
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.workspace.jps.entities.ModuleEntity
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.workspaceModel.ide.legacyBridge.impl.java.JAVA_MODULE_ENTITY_TYPE_ID
import java.util.concurrent.atomic.AtomicInteger

internal class CompletionSessionsCounter : LookupManagerListener {
  companion object {
    private const val NOTIFICATION_EXPIRED_KEY = "ml.local.models.training.notification.expired"
    private const val SHOW_NOTIFICATION_REGISTRY = "ml.local.models.show.notification"
    private const val MIN_SESSIONS_COUNT = 10
  }

  private val counter = AtomicInteger()
  private var isJavaProject = false

  override fun activeLookupChanged(oldLookup: Lookup?, newLookup: Lookup?) {
    if (newLookup == null && oldLookup != null) {
      val project = oldLookup.project
      val properties = PropertiesComponent.getInstance(project)

      if (!Registry.`is`(SHOW_NOTIFICATION_REGISTRY, false) || properties.getBoolean(NOTIFICATION_EXPIRED_KEY)) {
        return
      }

      if (counter.get() == 0) {
        isJavaProject = WorkspaceModel.getInstance(project).currentSnapshot.entities(ModuleEntity::class.java).any {
          it.type == JAVA_MODULE_ENTITY_TYPE_ID
        }
      }
      else if (!isJavaProject) {
        return
      }

      ReadAction.nonBlocking {
        if (oldLookup.items.any { it.psiElement is PsiMethod || it.psiElement is PsiClass } &&
            counter.incrementAndGet() >= MIN_SESSIONS_COUNT) {
          properties.setValue(NOTIFICATION_EXPIRED_KEY, true)
          TrainingNotification(project, JavaLanguage.INSTANCE).notify(project)
        }
      }.submit(AppExecutorUtil.getAppExecutorService())
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
        LocalModelsTraining.train(project, language)
        notification.expire()
      }
    })
    addAction(object : NotificationAction(MlLocalModelsBundle.message("ml.local.models.notification.cancel")) {
      override fun actionPerformed(e: AnActionEvent, notification: Notification) {
        notification.expire()
      }
    })
  }
}
