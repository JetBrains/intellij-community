// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.onboarding

import com.intellij.internal.statistic.DeviceIdManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.KotlinFileType
import java.time.*

class KotlinNewUserTrackerState : BaseState() {
    // Unix time seconds
    var firstKtFileOpened by property(0L)
    var lastKtFileOpened by property(0L)
    var newKtUserSince by property(0L)
}

@State(name = "KotlinNewUserTracker", storages = [Storage(value = "kotlin-onboarding.xml", roamingType = RoamingType.DISABLED)])
class KotlinNewUserTracker : PersistentStateComponent<KotlinNewUserTrackerState> {
    companion object {
        // Offer survey after one week of using Kotlin
        internal val NEW_USER_SURVEY_DELAY = Duration.ofDays(7)
        internal val NEW_IDEA_USER_DURATION = Duration.ofDays(30)

        // How long we will classify a user as new
        internal val NEW_USER_DURATION = Duration.ofDays(30)

        // After how long of a period of not using Kotlin at all we consider the user a new user again
        internal val NEW_USER_RESET = Duration.ofDays(90)

        private val LOG = Logger.getInstance(KotlinNewUserTracker::class.java)

        fun getInstance(): KotlinNewUserTracker {
            return service()
        }
    }

    // This is a var to allow unit tests to change this to a mock
    internal var deviceIdProvider: () -> String? = {
        DeviceIdManager.getOrGenerateId(object : DeviceIdManager.DeviceIdToken {}, "FUS")
    }

    internal var currentState: KotlinNewUserTrackerState = KotlinNewUserTrackerState()

    internal fun getInstallationId(): String? {
        return runCatching {
            deviceIdProvider()
        }.getOrNull()
    }

    /**
     * Returns the date on which the user installed IDEA, or null if it could not be determined.
     */
    @ApiStatus.Internal
    fun getInstallationDate(): LocalDate? {
        val installationId = getInstallationId() ?: return null
        val dateSubstring = installationId.take(6).takeIf { it.length == 6 } ?: return null
        val day = dateSubstring.substring(0..1).toIntOrNull() ?: return null
        val month = dateSubstring.substring(2..3).toIntOrNull() ?: return null
        val year = dateSubstring.substring(4..5).toIntOrNull() ?: return null

        return LocalDate.of(year + 2000, month, day)
    }

    /**
     * This is needed temporarily so that the survey is only shown to users who are entirely new to IDEA.
     * We will change it to also show it to old users who are new to Kotlin with an upcoming release.
     */
    internal fun isNewIdeaUser(): Boolean {
        val installationDate = getInstallationDate()
        if (installationDate == null) {
            LOG.debug("Could not get InstallationId for IDEA installation")
            return false
        }
        LOG.debug("Got user installation date: $installationDate")
        return Duration.between(installationDate.atStartOfDay(ZoneId.systemDefault()).toInstant(), Instant.now()) <= NEW_IDEA_USER_DURATION
    }

    override fun getState(): KotlinNewUserTrackerState = currentState

    override fun loadState(state: KotlinNewUserTrackerState) {
        currentState = state
    }

    /**
     * Returns the date on which the user was first detected to have used Kotlin.
     * Returns null if an error occurred, or the user has not used Kotlin before.
     *
     * Note: This data only started being tracked in 2023, so that is the minimum date that exists and can be returned from here.
     */
    @ApiStatus.Internal
    fun getFirstKotlinUsageDate(): LocalDate? {
        if (state.newKtUserSince == 0L) return null
        return Instant.ofEpochSecond(state.newKtUserSince).atOffset(ZoneOffset.UTC).toLocalDate()
    }

    internal fun isNewKtUser(): Boolean {
        if (state.newKtUserSince == 0L) return false
        val newUserStart = Instant.ofEpochSecond(state.newKtUserSince)
        return Duration.between(newUserStart, Instant.now()) <= NEW_USER_DURATION
    }

    internal fun shouldShowNewUserDialog(): Boolean {
        if (currentState.firstKtFileOpened == 0L) return false
        if (!isNewKtUser()) {
            LOG.debug("Not showing new user dialog because the user is not a new Kotlin user")
            return false
        }

        val newKtUserInstant = Instant.ofEpochSecond(currentState.newKtUserSince)
        val durationSinceNewKtUser = Duration.between(newKtUserInstant, Instant.now())

        LOG.debug("Duration since user became a new Kotlin user: ${durationSinceNewKtUser.toDays()} day(s)")
        return durationSinceNewKtUser > NEW_USER_SURVEY_DELAY
    }

    private fun checkForNewKtUser() {
        if (isNewKtUser()) {
            // No need to check if the user is already new
            return
        }
        val currentEpoch = Instant.now()
        // This part marks users who open a Kotlin file for the first time as new users
        if (currentState.newKtUserSince == 0L && currentState.firstKtFileOpened == 0L) {
            currentState.newKtUserSince = currentEpoch.epochSecond
            LOG.debug("Marking user as new Kotlin user because they are new to IDEA")
            return
        }
        // This part marks users as new Kotlin users, if they have not edited a Kotlin file in the past few months
        if (currentState.lastKtFileOpened == 0L) return
        val lastKtFileOpenedInstant = Instant.ofEpochSecond(currentState.lastKtFileOpened)
        val durationSinceLastKtFileOpened = Duration.between(lastKtFileOpenedInstant, currentEpoch)
        if (durationSinceLastKtFileOpened > NEW_USER_RESET) {
            LOG.debug("Marking user as new Kotlin user because they have not edited a Kotlin file in the past 3 months")
            currentState.newKtUserSince = currentEpoch.epochSecond
        }
    }

    internal fun onKtFileOpened() {
        checkForNewKtUser()

        val currentEpoch = Instant.now()
        currentState.lastKtFileOpened = currentEpoch.epochSecond
        if (currentState.firstKtFileOpened == 0L) {
            currentState.firstKtFileOpened = currentEpoch.epochSecond
            LOG.debug("Kotlin file opened by user for the first time")
        }
    }
}

class KotlinNewUserTrackerFileListener : FileEditorManagerListener {
    override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
        val app = ApplicationManager.getApplication()
        if (app.isUnitTestMode || app.isHeadlessEnvironment) return

        if (file.nameSequence.endsWith(KotlinFileType.DOT_DEFAULT_EXTENSION)) {
            val newUserTracker = KotlinNewUserTracker.getInstance()
            newUserTracker.onKtFileOpened()
        }
    }
}