// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.migration

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.internal.statistic.utils.getPluginInfoById
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinIdePlugin

object KotlinMigrationProjectFUSCollector : CounterUsagesCollector() {
    override fun getGroup(): EventLogGroup = GROUP

    private val GROUP = EventLogGroup("kotlin.ide.migrationTool", 3)

    private val oldLanguageVersion = EventFields.StringValidatedByRegexpReference("old_language_version", "version_lang_api")
    private val oldApiVersion = EventFields.StringValidatedByRegexpReference("old_api_version", "version_lang_api")
    private val pluginInfo = EventFields.PluginInfo

    private val notificationEvent = GROUP.registerVarargEvent(
        "Notification",
        oldLanguageVersion,
        oldApiVersion,
        pluginInfo,
    )

    private val runEvent = GROUP.registerEvent(
        "Run"
    )

    fun logNotification(migrationInfo: MigrationInfo) {
        notificationEvent.log(
            oldLanguageVersion.with(migrationInfo.oldLanguageVersion.versionString),
            oldApiVersion.with(migrationInfo.oldApiVersion.versionString),
            pluginInfo.with(getPluginInfoById(KotlinIdePlugin.id))
        )
    }

    fun logRun() {
        runEvent.log()
    }
}