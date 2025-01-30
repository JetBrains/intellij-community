// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.configuration

import com.intellij.model.SideEffectGuard
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectTracker
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.ui.EditorNotifications
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.projectConfiguration.KotlinProjectConfigurationBundle
import org.jetbrains.kotlin.idea.statistics.KotlinJ2KOnboardingFUSCollector
import org.jetbrains.kotlin.idea.util.isKotlinFileType
import java.util.concurrent.atomic.AtomicReference

@Service(Service.Level.PROJECT)
class KotlinProjectConfigurationService(private val project: Project, private val coroutineScope: CoroutineScope) {
    companion object {
        fun getInstance(project: Project): KotlinProjectConfigurationService {
            return project.service()
        }
    }

    private data class SyncState(
        val level: Level,

        /**
         * Jobs waiting for a sync to finish (aka transition back to 'idle' again)
         */
        val onFinishedListeners: List<CompletableJob>
    ) {
        enum class Level {
            /**
             * No sync is requested, nor currently in-progress running
             */
            Idle,

            /**
             * A sync is enqueued but not yet actually running on the external system, or
             * a previous sync is already running, but a new one has been requested already
             */
            SyncQueued,

            /**
             * The sync is currently running by the external system
             */
            SyncInProgress,
        }
    }

    private val syncState = AtomicReference(SyncState(SyncState.Level.Idle, emptyList()))

    @Volatile
    private var checkingAndPerformingAutoConfig: Boolean = false

    // A small cooldown after configuration was performed, to bridge the gap between the files being created and the Gradle sync starting
    @Volatile
    private var notificationCooldownEnd: Long? = null

    fun shouldShowNotConfiguredDialog(module: Module): Boolean {
        if (!findApplicableConfigurator(module).shouldShowNotConfiguredDialog()) return false
        if (isSyncDesired(module)) return false
        if (checkingAndPerformingAutoConfig) return false
        // If notificationCooldownEnd wasn't set, then the autoconfiguration didn't take place
        val cooldownEnd = notificationCooldownEnd ?: return true
        return System.currentTimeMillis() >= cooldownEnd
    }


    fun refreshEditorNotifications() {
        // We want to remove the "Kotlin not configured" notification banner as fast as possible
        // once a Gradle reload was started or Maven reload is pending.
        val openFiles = FileEditorManager.getInstance(project).openFiles
        val openKotlinFiles = openFiles.filter { it.isKotlinFileType() }
        if (openKotlinFiles.isEmpty()) return
        val editorNotifications = EditorNotifications.getInstance(project)

        openKotlinFiles.forEach {
            editorNotifications.updateNotifications(it)
        }
    }

    /**
     * @return true if the build configuration is 'dirty' and the user is shown a button to re-sync.
     * @see isSyncing
     * @see isSyncInProgress
     * @see KotlinBuildSystemDependencyManager.isProjectSyncPending
     */
    fun isSyncDesired(module: Module): Boolean {
        return KotlinBuildSystemDependencyManager.findApplicableConfigurator(module)?.isProjectSyncPending() == true
    }

    /**
     * @return true if the sync is already enqueued or actually in progress by the underlying external system.
     */
    fun isSyncing(): Boolean {
        return syncState.get().run {
            level == SyncState.Level.SyncQueued || level == SyncState.Level.SyncInProgress
        }
    }

    /**
     * @return true if the sync is currently in-progress by the underlying external system.
     * Also see [isSyncing] for checking if the sync is queued or in progress.
     */
    fun isSyncInProgress(): Boolean {
        return syncState.get().level == SyncState.Level.SyncInProgress
    }

    /**
     * Will suspend until all queued or 'in progress' syncs are completed.
     * @return true if the method suspended and had to wait for a sync
     * @return false if no sync was queued or in progress and the method was able to return without suspending.
     */
    suspend fun awaitSyncFinished(): Boolean {
        val job = Job()

        val state = syncState.updateAndGet { state ->
            /* Idle: Nothing to do */
            if (state.level == SyncState.Level.Idle) return@updateAndGet state
            state.copy(onFinishedListeners = state.onFinishedListeners + job)
        }

        if (state.level != SyncState.Level.Idle) {
            job.join()
            return true
        }

        return false
    }

    /**
     * Triggers a sync ([queueSync]) and will suspend until it completes.
     */
    suspend fun sync() {
        queueSync()
        awaitSyncFinished()
    }

    /**
     * Executes a build tool sync now, provided no sync is currently running.
     * Otherwise, waits for the current sync to finish and schedules a new sync.
     */
    fun queueSync() {
        // prevents this side effect from being actually run from quickfix previews (e.g. in Fleet)
        SideEffectGuard.checkSideEffectAllowed(SideEffectGuard.EffectType.PROJECT_MODEL)
        val previousState = syncState.getAndUpdate { state -> state.copy(level = SyncState.Level.SyncQueued) }
        if (previousState.level < SyncState.Level.SyncInProgress) {
            ExternalSystemProjectTracker.getInstance(project).scheduleProjectRefresh()
        }
    }

    @ApiStatus.Internal
    fun onSyncStarted() {
        syncState.updateAndGet { state ->
            state.copy(level = SyncState.Level.SyncInProgress)
        }
    }

    @ApiStatus.Internal
    fun onSyncFinished() {
        val previousState = syncState.getAndUpdate { state ->
            /* 
            Only if the previous state was actually 'SyncInProgress', then we can 
            consider the job to be done and reset the state. If the state is already 'SyncQueued' again, this means
            that another queueSync call happened and we should start one more sync.
             */
            if (state.level == SyncState.Level.SyncInProgress) {
                SyncState(level = SyncState.Level.Idle, emptyList())
            } else {
                state
            }
        }

        /*
        We previously were in 'StateInProgress', which means the job now has finished.
        Therefore, its time to complete the waiting coroutines.
         */
        if (previousState.level == SyncState.Level.SyncInProgress) {
            previousState.onFinishedListeners.forEach { job -> job.complete() }
        }

        /*
        During the current sync, we received another call into 'queueSync', which means 
        that the currently finished sync is 'dirty' and we should start another one!
         */
        if (previousState.level == SyncState.Level.SyncQueued) {
            ExternalSystemProjectTracker.getInstance(project).scheduleProjectRefresh()
        }
    }

    /**
     * Checks if the [module] can be auto-configured and runs auto-configuration if it is possible.
     * If the auto-configuration could not be run, a notification suggesting manual configuration
     * is displayed, if configuration is necessary.
     */
    fun runAutoConfigurationIfPossible(module: Module) {
        checkingAndPerformingAutoConfig = true
        // Removes the notification showing for a split second
        refreshEditorNotifications()
        coroutineScope.launch(Dispatchers.Default) {
            var configured = false
            try {
                val autoConfigurator = readAction {
                    KotlinProjectConfigurator.EP_NAME.extensionList
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
                checkingAndPerformingAutoConfig = false
                if (!configured) {
                    // Immediately refresh editor notifications to show Kotlin not-configured notification, if necessary
                    refreshEditorNotifications()
                    showConfigureKotlinNotificationIfNeeded(module)
                }
            }
        }
    }
}
