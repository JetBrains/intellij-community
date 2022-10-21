// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.statistics

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class MavenImportCollector : CounterUsagesCollector() {
  companion object {
    val GROUP = EventLogGroup("maven.import", 8)

    @JvmField
    val HAS_USER_ADDED_LIBRARY_DEP = GROUP.registerEvent("hasUserAddedLibraryDependency")

    @JvmField
    val HAS_USER_ADDED_MODULE_DEP = GROUP.registerEvent("hasUserAddedModuleDependency")

    @JvmField
    val HAS_USER_MODIFIED_IMPORTED_LIBRARY = GROUP.registerEvent("hasUserModifiedImportedLibrary")

    @JvmField
    val NUMBER_OF_MODULES = EventFields.RoundedInt("number_of_modules")

    // >>> Legacy import phases
    @JvmField
    val LEGACY_IMPORT = GROUP.registerIdeActivity("legacy_import",
                                                  finishEventAdditionalFields = arrayOf(NUMBER_OF_MODULES))

    @JvmField
    val LEGACY_CREATE_MODULES_PHASE = GROUP.registerIdeActivity("create_modules", parentActivity = LEGACY_IMPORT)

    @JvmField
    val LEGACY_DELETE_OBSOLETE_PHASE = GROUP.registerIdeActivity("delete_obsolete", parentActivity = LEGACY_IMPORT)

    @JvmField
    val LEGACY_IMPORTERS_PHASE = GROUP.registerIdeActivity("importers", parentActivity = LEGACY_IMPORT)
    // <<< Legacy import phases

    @JvmField
    val ACTIVITY_ID = EventFields.IdeActivityIdField

    // >>> Workspace import phases
    @JvmField
    val WORKSPACE_IMPORT = GROUP.registerIdeActivity("workspace_import",
                                                     finishEventAdditionalFields = arrayOf(NUMBER_OF_MODULES))

    @JvmField
    val WORKSPACE_FOLDERS_UPDATE = GROUP.registerIdeActivity("workspace_folders_update",
                                                             finishEventAdditionalFields = arrayOf(NUMBER_OF_MODULES))

    @JvmField
    val WORKSPACE_POPULATE_PHASE = GROUP.registerIdeActivity("populate", parentActivity = WORKSPACE_IMPORT)

    @JvmField
    val DURATION_BACKGROUND_MS = EventFields.Long("duration_in_background_ms")

    @JvmField
    val DURATION_WRITE_ACTION_MS = EventFields.Long("duration_in_write_action_ms")

    @JvmField
    val DURATION_OF_WORKSPACE_UPDATE_CALL_MS = EventFields.Long("duration_of_workspace_update_call_ms")

    @JvmField
    val ATTEMPTS = EventFields.Int("attempts")

    @JvmField
    val WORKSPACE_COMMIT_STATS = GROUP.registerVarargEvent("workspace_commit", ACTIVITY_ID, DURATION_BACKGROUND_MS,
                                                           DURATION_WRITE_ACTION_MS, DURATION_OF_WORKSPACE_UPDATE_CALL_MS, ATTEMPTS)

    @JvmField
    val WORKSPACE_COMMIT_PHASE = GROUP.registerIdeActivity("commit", parentActivity = WORKSPACE_IMPORT)

    @JvmField
    val WORKSPACE_LEGACY_IMPORTERS_PHASE = GROUP.registerIdeActivity("legacy_importers", parentActivity = WORKSPACE_IMPORT)
    // <<< Workspace import phases

    @JvmField
    val TOTAL_DURATION_MS = EventFields.Long("total_duration_ms")

    @JvmField
    val COLLECT_FOLDERS_DURATION_MS = EventFields.Long("collect_folders_duration_ms")

    @JvmField
    val CONFIG_MODULES_DURATION_MS = EventFields.Long("config_modules_duration_ms")

    @JvmField
    val BEFORE_APPLY_DURATION_MS = EventFields.Long("before_apply_duration_ms")

    @JvmField
    val AFTER_APPLY_DURATION_MS = EventFields.Long("after_apply_duration_ms")

    @JvmField
    val IMPORTER_CLASS = EventFields.Class("importer_class")

    @JvmField
    val IMPORTER_RUN = GROUP.registerVarargEvent("importer_run", ACTIVITY_ID, IMPORTER_CLASS, NUMBER_OF_MODULES, TOTAL_DURATION_MS)

    @JvmField
    val CONFIGURATOR_CLASS = EventFields.Class("configurator_class")

    @JvmField
    val CONFIGURATOR_RUN = GROUP.registerVarargEvent("workspace_import.configurator_run",
                                                     ACTIVITY_ID,
                                                     CONFIGURATOR_CLASS,
                                                     NUMBER_OF_MODULES,
                                                     TOTAL_DURATION_MS,
                                                     COLLECT_FOLDERS_DURATION_MS,
                                                     CONFIG_MODULES_DURATION_MS,
                                                     BEFORE_APPLY_DURATION_MS,
                                                     AFTER_APPLY_DURATION_MS)
  }

  override fun getGroup(): EventLogGroup {
    return GROUP
  }
}