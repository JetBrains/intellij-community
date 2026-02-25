// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pyproject.model.internal

import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.notification.impl.NotificationFullContent
import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.python.pyproject.PY_PROJECT_TOML
import com.intellij.python.pyproject.model.PyProjectModelSettings
import com.intellij.python.pyproject.model.PyProjectModelSettings.FeatureState.ASK
import com.intellij.python.pyproject.model.PyProjectModelSettings.FeatureState.OFF
import com.intellij.python.pyproject.model.PyProjectModelSettings.FeatureState.ON
import org.jetbrains.annotations.Nls

private const val NOTIFICATION_GROUP_ID = "PyProject.toml"

internal suspend fun askUserIfPyProjectMustBeEnabled(project: Project) {
  when (PyProjectModelSettings.featureStateInRegistry) {
    ON, OFF -> {
      return
    }
    ASK -> Unit
  }

  val settings = PyProjectModelSettings.getInstance(project)
  if (!settings.showConfigurationNotification) return

  val hasPyprojectToml = readAction {
    !project.isDisposed && FilenameIndex.getVirtualFilesByName(PY_PROJECT_TOML, GlobalSearchScope.projectScope(project)).isNotEmpty()
  }

  if (hasPyprojectToml) {
    showNotification(project, settings)
  }
  else {
    listenForPyprojectToml(project, settings)
  }
}

private fun listenForPyprojectToml(project: Project, settings: PyProjectModelSettings) {
  val disposable = Disposer.newDisposable("PyProjectModelStartupActivity")
  Disposer.register(settings, disposable)

  project.messageBus.connect(disposable).subscribe(DumbService.DUMB_MODE, object : DumbService.DumbModeListener {
    override fun exitDumbMode() {
      if (!settings.showConfigurationNotification) {
        Disposer.dispose(disposable)
        return
      }

      val hasAnyPyprojectToml = FilenameIndex.hasVirtualFileWithName(
        PY_PROJECT_TOML,
        true,
        GlobalSearchScope.projectScope(project),
        null
      )

      if (hasAnyPyprojectToml) {
        Disposer.dispose(disposable)
        showNotification(project, settings)
      }
    }
  })
}

private fun showNotification(project: Project, settings: PyProjectModelSettings) {
  FullContentNotification(
    NOTIFICATION_GROUP_ID,
    PyProjectTomlBundle.message("pyproject.notification.title"),
    PyProjectTomlBundle.message("pyproject.notification.content"),
    NotificationType.INFORMATION,
  )
    .setSuggestionType(true)
    .addAction(NotificationAction.createSimpleExpiring(PyProjectTomlBundle.message("pyproject.notification.configure")) {
      settings.usePyprojectToml = true
      settings.showConfigurationNotification = false
    })
    .addAction(NotificationAction.createSimpleExpiring(PyProjectTomlBundle.message("pyproject.notification.dont.show.again")) {
      settings.showConfigurationNotification = false
    })
    .notify(project)
}

private class FullContentNotification(groupId: String, @Nls title: String, @Nls content: String, type: NotificationType) :
  Notification(groupId, title, content, type), NotificationFullContent
