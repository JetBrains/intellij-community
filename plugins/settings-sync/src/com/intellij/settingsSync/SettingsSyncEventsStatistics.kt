package com.intellij.settingsSync

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector

class SettingsSyncEventsStatistics : CounterUsagesCollector() {
  companion object {
    val GROUP: EventLogGroup = EventLogGroup("settings.sync.events", 1)

    val ENABLED_MANUALLY = GROUP.registerEvent("enabled.manually", EventFields.Enum("method", EnabledMethod::class.java))
    val DISABLED_MANUALLY = GROUP.registerEvent("disabled.manually", EventFields.Enum("method", ManualDisableMethod::class.java))
    val DISABLED_AUTOMATICALLY = GROUP.registerEvent("disabled.automatically", EventFields.Enum("reason", AutomaticDisableReason::class.java))
    val MIGRATED_FROM_OLD_PLUGIN = GROUP.registerEvent("migrated.from.old.plugin")
    val MIGRATED_FROM_SETTINGS_REPOSITORY = GROUP.registerEvent("migrated.from.settings.repository")
    val SETTINGS_REPOSITORY_NOTIFICATION_ACTION = GROUP.registerEvent("invoked.settings.repository.notification.action", EventFields.Enum("action", SettingsRepositoryMigrationNotificationAction::class.java))
  }

  enum class EnabledMethod {
    GET_FROM_SERVER,
    PUSH_LOCAL,
    PUSH_LOCAL_WAS_ONLY_WAY,
    CANCELED
  }

  enum class ManualDisableMethod {
    DISABLED_ONLY,
    DISABLED_AND_REMOVED_DATA_FROM_SERVER,
    CANCEL
  }

  enum class AutomaticDisableReason {
    REMOVED_FROM_SERVER,
    EXCEPTION
  }

  enum class SettingsRepositoryMigrationNotificationAction {
    INSTALL_SETTINGS_REPOSITORY,
    USE_NEW_SETTINGS_SYNC
  }

  override fun getGroup(): EventLogGroup {
    return GROUP
  }
}