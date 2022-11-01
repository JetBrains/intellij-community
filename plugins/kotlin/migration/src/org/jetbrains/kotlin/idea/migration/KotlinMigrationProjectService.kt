// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.migration

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.AppExecutorUtil
import org.jetbrains.kotlin.cli.common.arguments.CommonCompilerArguments
import org.jetbrains.kotlin.config.ApiVersion
import org.jetbrains.kotlin.config.LanguageVersion
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinCompilerSettingsListener
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinPluginLayout
import org.jetbrains.kotlin.idea.configuration.notifications.showMigrationNotification
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode

class KotlinMigrationProjectService(val project: Project) : Disposable {
    private var currentState: MigrationState? = null

    private fun updateState(languageVersion: LanguageVersion?, apiVersion: ApiVersion?) {
        val newState = if (languageVersion == null || apiVersion == null) {
            val bundledKotlinVersion = KotlinPluginLayout.standaloneCompilerVersion
            MigrationState(
                languageVersion = languageVersion ?: bundledKotlinVersion.languageVersion,
                apiVersion = apiVersion ?: bundledKotlinVersion.apiVersion,
            )
        } else {
            MigrationState(languageVersion, apiVersion)
        }

        val oldState = synchronized(this) { currentState.also { currentState = newState } }
        val migrationInfo = prepareMigrationInfo(old = oldState, new = newState) ?: return
        ReadAction.nonBlocking<Boolean> { applicableMigrationToolExists(migrationInfo) || isUnitTestMode() }
            .inSmartMode(project)
            .finishOnUiThread(ModalityState.any()) { toolExists ->
                if (toolExists) {
                    showMigrationNotification(project, migrationInfo)
                }
            }
            .submit(AppExecutorUtil.getAppExecutorService())
    }

    override fun dispose() = Unit

    internal class CommonCompilerSettingsChangeListener(private val project: Project) : KotlinCompilerSettingsListener {
        override fun <T> settingsChanged(oldSettings: T?, newSettings: T?) {
            val newCommonSettings = newSettings as? CommonCompilerArguments.DummyImpl ?: return
            if (!CodeMigrationToggleAction.isEnabled(project)) return
            project.service<KotlinMigrationProjectService>().updateState(
                newCommonSettings.languageVersion?.let { LanguageVersion.fromVersionString(it) },
                newCommonSettings.apiVersion?.let { ApiVersion.parse(it) },
            )
        }
    }
}

private fun prepareMigrationInfo(old: MigrationState?, new: MigrationState?): MigrationInfo? {
    if (old == null || new == null) return null

    if (old.apiVersion < new.apiVersion || old.languageVersion < new.languageVersion) {
        return MigrationInfo(
            oldApiVersion = old.apiVersion,
            newApiVersion = new.apiVersion,
            oldLanguageVersion = old.languageVersion,
            newLanguageVersion = new.languageVersion,
        )
    }

    return null
}

private class MigrationState(
    val languageVersion: LanguageVersion,
    val apiVersion: ApiVersion,
)