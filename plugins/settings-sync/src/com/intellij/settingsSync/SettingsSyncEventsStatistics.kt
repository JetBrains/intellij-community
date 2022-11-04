package com.intellij.settingsSync

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.FeatureUsagesCollector

class SettingsSyncEventsStatistics : FeatureUsagesCollector() {
  companion object {
    val GROUP: EventLogGroup = EventLogGroup("settingsSyncEvents", 1)

    val ENABLED_MANUALLY = GROUP.registerEvent("enabled_manually", EventFields.Enum("enabled_method", EnabledMethod::class.java))
    val DISABLED_MANUALLY = GROUP.registerEvent("disabled_manually", EventFields.Enum("disabled_method", DisabledMethod::class.java))
    val DISABLED_BECAUSE_REMOVED_FROM_SERVER = GROUP.registerEvent("disabled_from_server")
    val DISABLED_BECAUSE_OF_EXCEPTION = GROUP.registerEvent("disabled_by_exception")
    val MIGRATED_FROM_OLD_PLUGIN = GROUP.registerEvent("migrated_from_old_plugin")
    val MIGRATED_FROM_SETTINGS_REPOSITORY = GROUP.registerEvent("migrated_from_settings_repository")
    val SETTINGS_REPOSITORY_NOTIFICATION_ACTION =
      GROUP.registerEvent("settings_repository_notification_action", EventFields.Enum("action", SettingsRepositoryMigrationNotificationAction::class.java))
  }

  enum class EnabledMethod {
    GET_FROM_SERVER,
    PUSH_LOCAL,
    PUSH_LOCAL_WAS_ONLY_WAY,
    CANCELED
  }

  enum class DisabledMethod {
    DISABLED_ONLY,
    DISABLED_AND_REMOVED_DATA_FROM_SERVER,
    CANCEL
  }

  enum class SettingsRepositoryMigrationNotificationAction {
    INSTALL_SETTINGS_REPOSITORY,
    USE_NEW_SETTINGS_SYNC
  }

  override fun getGroup(): EventLogGroup {
    return GROUP
  }
}