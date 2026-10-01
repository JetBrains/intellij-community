// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.formatter

import com.intellij.application.options.CodeStyle
import com.intellij.formatting.service.CoreFormattingService
import com.intellij.formatting.service.FormattingServiceUtil
import com.intellij.ide.util.PropertiesComponent
import com.intellij.idea.AppMode
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.AnActionResult
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.InitialConfigImportState
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.codeStyle.CodeStyleSchemes
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageEditorUtil
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PythonLanguage
import java.util.concurrent.atomic.AtomicBoolean

private const val MIGRATION_DONE_PROPERTY = "py.code.style.new.defaults.migration.done"
private const val NOTIFICATION_GROUP_ID = "Python code style"

/**
 * Rolls out the PY-85946 new (industry-standard) Python formatter defaults, gated behind the master
 * switch [isPyNewFormatterDefaultsFeatureEnabled] ([PY_NEW_FORMATTER_DEFAULTS_ENABLED_KEY], flag 1):
 *
 * - a brand-new installation silently activates the new defaults (flag 2 on) application-wide;
 * - an upgrading installation keeps classic. [PyNewFormatterDefaultsReformatListener] offers it a one-time
 *   switch balloon on the first Python reformat, not at startup.
 *
 * The decision is made once per installation. Only the IDE-level Default scheme is ever changed.
 * Other IDE-level schemes and per-project code style configurations are never touched.
 *
 * The activity also runs on a remote development backend, which is a headless application.
 */
internal class PyCodeStyleMigrationActivity : ProjectActivity {
  override suspend fun execute(project: Project) {
    val application = ApplicationManager.getApplication()
    if (application.isUnitTestMode || (application.isHeadlessEnvironment && !AppMode.isRemoteDevHost())) return
    if (!isMigrationPending()) return

    if (InitialConfigImportState.isNewUser()) {
      // Fresh installation: adopt the new defaults application-wide; no balloon, now or for later projects.
      // There is nothing for the user to react to, so the decision is recorded right away.
      PropertiesComponent.getInstance().setValue(MIGRATION_DONE_PROPERTY, true)
      useNewDefaults()
    }
  }
}

/**
 * Offers the switch balloon to an upgrading installation after the first "Reformat Code" in a Python editor.
 *
 * Only a reformat from an editor counts. A reformat from the Project view opens a dialog that the user can cancel,
 * and the action reports it as performed either way. These do not count either: the "Reformat File" dialog,
 * the reformat on save, and the reformat in the commit dialog. While a user does not see the balloon,
 * the `python.formatter.use.new.defaults` advanced setting gives the same choice.
 *
 * The balloon does not show when an alternative formatter (for example, Black or Ruff) formats the file,
 * because the Python code style settings have no effect on that formatter. It also does not show when
 * the project uses a scheme other than the IDE-level Default scheme, because the switch has no effect there.
 */
internal class PyNewFormatterDefaultsReformatListener : AnActionListener {
  // The platform creates one application listener instance, so this guard offers the balloon once per IDE run.
  private val balloonShownThisSession = AtomicBoolean(false)

  override fun afterActionPerformed(action: AnAction, event: AnActionEvent, result: AnActionResult) {
    if (balloonShownThisSession.get() || !result.isPerformed) return
    if (event.actionManager.getId(action) != IdeActions.ACTION_EDITOR_REFORMAT) return
    if (!isMigrationPending()) return

    val project = event.project ?: return
    val editor = event.getData(CommonDataKeys.EDITOR) ?: return
    val isFormattedByPyCharm = runReadActionBlocking {
      // The action uses the injected context, so the editor can belong to an injected fragment.
      val hostDocument = InjectedLanguageEditorUtil.getTopLevelEditor(editor).document
      val file = PsiDocumentManager.getInstance(project).getPsiFile(hostDocument)
      file != null &&
      file.language.isKindOf(PythonLanguage.getInstance()) &&
      FormattingServiceUtil.findService(file, true, true) is CoreFormattingService
    }
    if (!isFormattedByPyCharm || !usesDefaultScheme(project)) return

    if (balloonShownThisSession.compareAndSet(false, true)) {
      showSwitchBalloon(project)
    }
  }
}

/**
 * Stores the [PY_NEW_FORMATTER_DEFAULTS_SETTING_ID] advanced setting: the per-installation rollout state (flag 2).
 * The setting shows only while the master switch is on.
 *
 * A change applies the matching profile to the IDE-level Default scheme. While the master switch is off, the feature
 * is inert, so a change stores the value and does not change the scheme.
 */
@Suppress("unused") // The advanced setting calls the property and the visibility check by reflection.
@Service(Service.Level.APP)
@State(name = "PyNewFormatterDefaults", storages = [Storage("python-formatter.xml", roamingType = RoamingType.DISABLED)])
internal class PyNewFormatterDefaultsSettings : SimplePersistentStateComponent<PyNewFormatterDefaultsSettings.SettingsState>(SettingsState()) {
  class SettingsState : BaseState() {
    var useNewDefaults: Boolean by property(false)
  }

  var useNewDefaults: Boolean
    get() = state.useNewDefaults
    set(value) {
      if (value == state.useNewDefaults) return
      state.useNewDefaults = value
      if (!isPyNewFormatterDefaultsFeatureEnabled()) return
      // A choice in Advanced Settings replaces the switch balloon.
      PropertiesComponent.getInstance().setValue(MIGRATION_DONE_PROPERTY, true)
      applyProfileToDefaultScheme(value)
    }

  fun isUseNewDefaultsVisible(): Boolean = isPyNewFormatterDefaultsFeatureEnabled()
}

/** Turns on the new defaults. [PyNewFormatterDefaultsSettings] applies the modern profile to the IDE-level Default scheme. */
private fun useNewDefaults() {
  AdvancedSettings.setBoolean(PY_NEW_FORMATTER_DEFAULTS_SETTING_ID, true)
}

private fun isMigrationPending(): Boolean =
  isPyNewFormatterDefaultsFeatureEnabled() &&
  !AdvancedSettings.getBoolean(PY_NEW_FORMATTER_DEFAULTS_SETTING_ID) &&
  !PropertiesComponent.getInstance().getBoolean(MIGRATION_DONE_PROPERTY, false)

/**
 * Tells if the project formats with the IDE-level Default scheme. The temporary settings of a test do not count.
 */
private fun usesDefaultScheme(project: Project): Boolean {
  if (CodeStyle.usesOwnSettings(project)) return false
  val preferredScheme = CodeStyleSettingsManager.getInstance(project).PREFERRED_PROJECT_CODE_STYLE
  return CodeStyleSchemes.getInstance().findPreferredScheme(preferredScheme).isDefault
}

/**
 * Applies the profile to the IDE-level Default scheme. Other IDE-level schemes and per-project code style
 * configurations are never touched.
 *
 * The profile replaces all Python values of the Default scheme, so the user's changes to them are lost.
 */
private fun applyProfileToDefaultScheme(active: Boolean) {
  // CodeStyle.getDefaultSettings() returns the selected IDE-level scheme, which can be a custom scheme.
  val settings = CodeStyleSchemes.getInstance().defaultScheme.codeStyleSettings
  if (active) PyDefaultStyleGuide.apply(settings) else PyClassicStyleGuide.apply(settings)
  // The scheme settings are persisted in place. The notification applies the change without a restart.
  CodeStyleSettingsManager.getInstance().notifyCodeStyleSettingsChanged()
}

private fun showSwitchBalloon(project: Project) {
  // The decision is recorded when the user reacts through either action. Closing the balloon with its
  // "×" button records nothing, so the offer is shown again on a reformat in a later session.
  val properties = PropertiesComponent.getInstance()
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
      useNewDefaults()
    })
    .addAction(NotificationAction.createSimpleExpiring(PyBundle.message("python.code.style.migration.action.dismiss")) {
      properties.setValue(MIGRATION_DONE_PROPERTY, true)
    })
    .notify(project)
}
