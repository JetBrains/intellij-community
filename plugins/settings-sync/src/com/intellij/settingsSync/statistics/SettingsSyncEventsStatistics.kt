package com.intellij.settingsSync.statistics

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector

object SettingsSyncEventsStatistics : CounterUsagesCollector() {
  val GROUP: EventLogGroup = EventLogGroup("settings.sync.events", 4)

  val ENABLED_MANUALLY = GROUP.registerEvent("enabled.manually", EventFields.Enum("method", EnabledMethod::class.java))
  val DISABLED_MANUALLY = GROUP.registerEvent("disabled.manually", EventFields.Enum("method", ManualDisableMethod::class.java))
  val DISABLED_AUTOMATICALLY = GROUP.registerEvent("disabled.automatically", EventFields.Enum("reason", AutomaticDisableReason::class.java))
  val MIGRATED_FROM_OLD_PLUGIN = GROUP.registerEvent("migrated.from.old.plugin")
  val MIGRATED_FROM_SETTINGS_REPOSITORY = GROUP.registerEvent("migrated.from.settings.repository")
  val SETTINGS_REPOSITORY_NOTIFICATION_ACTION = GROUP.registerEvent("invoked.settings.repository.notification.action",
                                                                    EventFields.Enum("action",
                                                                                     SettingsRepositoryMigrationNotificationAction::class.java))
  val PROMOTION_IN_SETTINGS = GROUP.registerEvent("promotion.in.settings.event.happened",
                                                  EventFields.Enum("event", PromotionInSettingsEvent::class.java))
  val MERGE_CONFLICT_OCCURRED = GROUP.registerEvent("merge.conflict.occurred", EventFields.Enum("type", MergeConflictType::class.java))

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

  enum class PromotionInSettingsEvent {
    SHOWN,
    GO_TO_SETTINGS_SYNC,
    SKIP,
    ENABLED
  }

  enum class MergeConflictType {
    OPTIONS,
    SCHEMES,
    PLUGINS_JSON
  }

  override fun getGroup(): EventLogGroup {
    return GROUP
  }
}