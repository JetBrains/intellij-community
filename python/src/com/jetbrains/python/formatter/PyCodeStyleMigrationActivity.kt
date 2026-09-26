// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.formatter

import com.intellij.application.options.CodeStyle
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.InitialConfigImportState
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import com.jetbrains.python.PyBundle
import java.util.concurrent.atomic.AtomicBoolean

private const val MIGRATION_DONE_PROPERTY = "py.code.style.new.defaults.migration.done"
private const val NOTIFICATION_GROUP_ID = "Python code style"

/**
 * Rolls out the PY-85946 new (industry-standard) Python formatter defaults, gated behind the master
 * switch [isPyNewFormatterDefaultsFeatureEnabled] ([PY_NEW_FORMATTER_DEFAULTS_ENABLED_KEY], off by
 * default — flag 1):
 *
 * - a brand-new installation silently activates the new defaults (flag 2 on) application-wide;
 * - an upgrading installation keeps classic and is offered a one-time balloon to switch.
 *
 * The decision is made once per installation. Only the IDE-level (application) default code style is
 * ever changed — per-project code style configurations are never touched.
 */
internal class PyCodeStyleMigrationActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    val application = ApplicationManager.getApplication()
    if (application.isUnitTestMode || application.isHeadlessEnvironment) return
    if (!isPyNewFormatterDefaultsFeatureEnabled()) return  // master switch (flag 1) off → feature is inert

    val properties = PropertiesComponent.getInstance()
    if (properties.getBoolean(MIGRATION_DONE_PROPERTY, false)) return

    if (InitialConfigImportState.isNewUser()) {
      // Fresh installation: adopt the new defaults application-wide; no balloon, now or for later projects.
      // There is nothing for the user to react to, so the decision is recorded right away.
      properties.setValue(MIGRATION_DONE_PROPERTY, true)
      activateNewDefaults()
    }
    else {
      // Upgrading installation: keep classic and offer a one-time switch. The decision is recorded only
      // once the user actually reacts to the balloon (see showSwitchBalloon), so a balloon that is
      // ignored or goes unnoticed is offered again on the next start instead of being lost forever.
      // Guard against showing more than one balloon per session when several projects are opened.
      if (balloonShownThisSession.compareAndSet(false, true)) {
        showSwitchBalloon(project, properties)
      }
    }
  }

  /**
   * Turns the per-installation rollout state (flag 2) on and applies the modern profile to the
   * application-wide default code style. Per-project code style configurations are never touched.
   */
  private fun activateNewDefaults() {
    Registry.get(PY_NEW_FORMATTER_DEFAULTS_ACTIVE_KEY).setValue(true)
    PyDefaultStyleGuide.apply(CodeStyle.getDefaultSettings())
    // CodeStyle.getDefaultSettings() is the persisted application-level settings instance, so mutating
    // it in place is the supported way to change the app-wide default; notify listeners so the change is
    // picked up without a restart.
    CodeStyleSettingsManager.getInstance().notifyCodeStyleSettingsChanged()
  }

  private fun showSwitchBalloon(project: Project, properties: PropertiesComponent) {
    // The decision is recorded when the user reacts through either action. Closing the balloon with its
    // "×" button records nothing, so the offer is shown again on the next start.
    NotificationGroupManager.getInstance()
      .getNotificationGroup(NOTIFICATION_GROUP_ID)
      .createNotification(
        PyBundle.message("python.code.style.migration.title"),
        PyBundle.message("python.code.style.migration.content"),
        NotificationType.INFORMATION,
      )
      .setDisplayId("python.code.style.migration")
      .addAction(NotificationAction.createSimpleExpiring(PyBundle.message("python.code.style.migration.action.switch")) {
        properties.setValue(MIGRATION_DONE_PROPERTY, true)
        activateNewDefaults()
      })
      .addAction(NotificationAction.createSimpleExpiring(PyBundle.message("python.code.style.migration.action.dismiss")) {
        properties.setValue(MIGRATION_DONE_PROPERTY, true)
      })
      .notify(project)
  }

  companion object {
    // Session-wide guard so opening several projects in one IDE run offers the switch balloon only once.
    private val balloonShownThisSession = AtomicBoolean(false)
  }
}
