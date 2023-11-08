// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.configuration

import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectNotificationAware
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectTracker
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.module.Module
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.openapi.project.Project
import com.intellij.ui.EditorNotifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.projectConfiguration.KotlinProjectConfigurationBundle
import org.jetbrains.kotlin.idea.statistics.KotlinJ2KOnboardingFUSCollector
import org.jetbrains.kotlin.idea.util.isKotlinFileType

@Service(Service.Level.PROJECT)
class KotlinProjectConfigurationService(private val project: Project, private val coroutineScope: CoroutineScope) {
    companion object {
        fun getInstance(project: Project): KotlinProjectConfigurationService {
            return project.service()
        }
    }

    @Volatile
    private var checkingAutoConfig: Boolean = false

    // A small cooldown after configuration was performed, to bridge the gap between the files being created and the Gradle sync starting
    @Volatile
    private var notificationCooldownEnd: Long? = null

    fun shouldShowNotConfiguredDialog(): Boolean {
        if (checkingAutoConfig) return false
        val cooldownEnd = notificationCooldownEnd ?: return true
        return System.currentTimeMillis() >= cooldownEnd
    }

    fun isGradleSyncPending(): Boolean {
        val notificationVisibleProperty =
            ExternalSystemProjectNotificationAware.isNotificationVisibleProperty(project, ProjectSystemId("GRADLE", "Gradle"))
        return notificationVisibleProperty.get()
    }

    fun refreshEditorNotifications() {
        // We want to remove the "Kotlin not configured" notification banner as fast as possible
        // once a gradle reload was started.
        val openFiles = FileEditorManager.getInstance(project).openFiles
        val openKotlinFiles = openFiles.filter { it.isKotlinFileType() }
        if (openKotlinFiles.isEmpty()) return
        val editorNotifications = EditorNotifications.getInstance(project)

        openKotlinFiles.forEach {
            editorNotifications.updateNotifications(it)
        }
    }

    @Volatile
    private var gradleSyncInProgress: Boolean = false

    @Volatile
    private var gradleSyncQueued: Boolean = false

    fun isGradleSyncInProgress(): Boolean {
        return gradleSyncInProgress
    }

    /**
     * Executes a Gradle sync now, provided no Gradle sync is currently running.
     * Otherwise, waits for the current sync to finish and schedules a new sync.
     */
    fun queueGradleSync() {
        if (gradleSyncInProgress) {
            gradleSyncQueued = true
        } else {
            ExternalSystemProjectTracker.getInstance(project).scheduleProjectRefresh()
        }
    }

    @ApiStatus.Internal
    fun onGradleSyncStarted() {
        gradleSyncInProgress = true
    }

    @ApiStatus.Internal
    fun onGradleSyncFinished() {
        gradleSyncInProgress = false
        if (gradleSyncQueued) {
            gradleSyncQueued = false
            ExternalSystemProjectTracker.getInstance(project).scheduleProjectRefresh()
        }
    }

    /**
     * Checks if the [module] can be auto-configured and runs auto-configuration if it is possible.
     * If the auto-configuration could not be run, a notification suggesting manual configuration
     * is displayed, if configuration is necessary.
     */
    fun runAutoConfigurationIfPossible(module: Module) {
        checkingAutoConfig = true
        // Removes the notification showing for a split second
        refreshEditorNotifications()
        coroutineScope.launch(Dispatchers.Default) {
            var configured = false
            try {
                val autoConfigurator = readAction {
                    KotlinProjectConfigurator.EP_NAME.extensions
                        .firstOrNull { it.canRunAutoConfig() && it.isApplicable(module) }
                } ?: return@launch

                val autoConfigSettings = withBackgroundProgress(
                    project = module.project,
                    title = KotlinProjectConfigurationBundle.message("auto.configure.kotlin.check")
                ) {
                    val settings = autoConfigurator.calculateAutoConfigSettings(module)
                    KotlinJ2KOnboardingFUSCollector.logCheckAutoConfigStatus(module.project, settings != null)
                    settings
                }

                if (autoConfigSettings == null) return@launch
                autoConfigurator.runAutoConfig(autoConfigSettings)
                configured = true
                notificationCooldownEnd = System.currentTimeMillis() + 2000
            } finally {
                checkingAutoConfig = false
                if (!configured) {
                    // Immediately refresh editor notifications to show Kotlin not-configured notification, if necessary
                    refreshEditorNotifications()
                    readAction {
                        showConfigureKotlinNotificationIfNeeded(module)
                    }
                }
            }
        }
    }
}