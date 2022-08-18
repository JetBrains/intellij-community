// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.tools.projectWizard.wizard

import org.jetbrains.kotlin.idea.statistics.WizardLoggingSession
import org.jetbrains.kotlin.idea.statistics.WizardStatsService
import org.jetbrains.kotlin.tools.projectWizard.core.entity.settings.SettingReference
import org.jetbrains.kotlin.tools.projectWizard.projectTemplates.ProjectTemplate
import org.jetbrains.kotlin.tools.projectWizard.settings.version.Version
import org.jetbrains.kotlin.tools.projectWizard.templates.Template
import java.nio.file.Path

object OnUserSettingChangeStatisticsLogger {
    fun <V: Any> logSettingValueChangedByUser(session: WizardLoggingSession, reference: SettingReference<V, *>, value: V) {
        logSettingValueChangedByUser(session, reference.path, value)
    }

    fun <V: Any> logSettingValueChangedByUser(session: WizardLoggingSession, settingId: String, value: V) {
        val id = settingId.idForFus()
        val stringValue = value.getAsSettingValueIfAcceptable() ?: return
        WizardStatsService.logDataOnSettingValueChanged(session, id, stringValue)
    }

    private fun String.idForFus() =
        substringAfterLast("/")

    private fun Any.getAsSettingValueIfAcceptable() = when (this) {
        is Boolean -> toString()
        is Enum<*> -> toString()
        is ProjectTemplate -> id
        is Template -> id
        is String -> null
        is Path -> null
        is Version -> null
        is List<*> -> null
        else -> error("Unknown setting value ${this::class.simpleName}")
    }
}