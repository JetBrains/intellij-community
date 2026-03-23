// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.pyproject.model.internal

import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.notification.impl.NotificationFullContent
import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.components.service
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.intellij.python.pyproject.statistics.PyProjectTomlCollector
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.BufferOverflow.DROP_LATEST
import kotlinx.coroutines.flow.MutableSharedFlow
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

  if (hasPyprojectToml(project)) {
    showNotification(project, settings)
  }
  else {
    listenForPyprojectToml(project, settings)
  }
}

/**
 * Listens for `pyproject.toml` appearing in the project after startup.
 *
 * Subscribes to [DumbService.DUMB_MODE] and checks the filename index each time the IDE
 * exits dumb mode (i.e., indexing completes).
 *
 * Uses [MutableSharedFlow] with `replay = 1` so that an event emitted before
 * [collect][kotlinx.coroutines.flow.collect] starts is not lost.
 * [DROP_LATEST][BufferOverflow.DROP_LATEST] silently drops overflow since all events
 * are identical `Unit` signals.
 */
private fun listenForPyprojectToml(project: Project, settings: PyProjectModelSettings) {
  val disposable = Disposer.newDisposable("PyProjectModelStartupActivity")
  Disposer.register(settings, disposable)

  val dumbModeExited = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = DROP_LATEST)

  project.messageBus.connect(disposable).subscribe(DumbService.DUMB_MODE, object : DumbService.DumbModeListener {
    override fun exitDumbMode() {
      dumbModeExited.tryEmit(Unit)
    }
  })

  project.service<PyProjectScopeService>().scope.launch {
    // Suspend until pyproject.toml appears in the index (checked after each dumb mode exit).
    // The result is unused — we only care about the side effects of waiting.
    dumbModeExited.first { hasPyprojectToml(project) }
    Disposer.dispose(disposable)
    showNotification(project, settings)
  }
}

private suspend fun hasPyprojectToml(project: Project): Boolean =
  smartReadAction(project) {
    !project.isDisposed &&
    FilenameIndex.hasVirtualFileWithName(PY_PROJECT_TOML, true, GlobalSearchScope.projectScope(project), null)
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
      PyProjectTomlCollector.setupNotificationConfigureClicked()
    })
    .addAction(NotificationAction.createSimpleExpiring(PyProjectTomlBundle.message("pyproject.notification.dont.show.again")) {
      settings.showConfigurationNotification = false
      PyProjectTomlCollector.setupNotificationDismissClicked()
    })
    .notify(project)
  PyProjectTomlCollector.setupNotificationShown()
}

private class FullContentNotification(groupId: String, @Nls title: String, @Nls content: String, type: NotificationType) :
  Notification(groupId, title, content, type), NotificationFullContent
