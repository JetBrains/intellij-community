// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.onboarding.gradle

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.modules
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.configuration.BuildSystemType
import org.jetbrains.kotlin.idea.configuration.buildSystemType
import org.jetbrains.kotlin.idea.configuration.hasKotlinPluginEnabled
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

internal class BuildProcessSatisfactionSurveyState : BaseState() {
    var daysWithGradleBuilds by property(0)
    var userSawSurvey by property(false)
    // Unix time seconds
    var firstKotlinGradleBuildTime by property(0L)
    // Not just Kotlin but also counts Java
    var firstGradleBuildTime by property(0L)
    var lastKotlinGradleBuildTime by property(0L)
    var nextCountedGradleBuild by property(0L)
}

internal fun Project.isKotlinGradleProject(): Boolean {
    return modules.any {
        it.buildSystemType == BuildSystemType.Gradle && it.hasKotlinPluginEnabled()
    }
}

@ApiStatus.Internal
@State(name = "BuildProcessSatisfactionSurveyStore", storages = [Storage(value = "kotlin-onboarding.xml", roamingType = RoamingType.DISABLED)])
internal class BuildProcessSatisfactionSurveyStore : PersistentStateComponent<BuildProcessSatisfactionSurveyState> {
    override fun getState(): BuildProcessSatisfactionSurveyState = currentState

    companion object {
        internal const val MINIMUM_BUILDS_BEFORE_SURVEY = 3
        internal val MINIMUM_DURATION_SINCE_FIRST_BUILD = Duration.ofDays(14)

        fun getInstance(): BuildProcessSatisfactionSurveyStore {
            return service()
        }
    }

    internal var currentState: BuildProcessSatisfactionSurveyState = BuildProcessSatisfactionSurveyState()

    override fun loadState(state: BuildProcessSatisfactionSurveyState) {
        currentState = state
    }

    internal fun recordSurveyShown() {
        state.userSawSurvey = true
    }

    internal fun recordNonKotlinBuild() {
        val currentTime = Instant.now()
        if (currentState.firstGradleBuildTime == 0L) {
            currentState.firstGradleBuildTime = currentTime.epochSecond
        }
    }

    internal fun recordKotlinBuild() {
        val currentTime = Instant.now()
        if (currentTime >= Instant.ofEpochSecond(currentState.nextCountedGradleBuild)) {
            currentState.daysWithGradleBuilds++
            currentState.nextCountedGradleBuild = (currentTime + Duration.ofDays(1)).epochSecond
        }
        currentState.lastKotlinGradleBuildTime = currentTime.epochSecond
        if (currentState.firstKotlinGradleBuildTime == 0L) {
            currentState.firstKotlinGradleBuildTime = currentTime.epochSecond
        }
        if (currentState.firstGradleBuildTime == 0L) {
            currentState.firstGradleBuildTime = currentTime.epochSecond
        }
    }

    /**
     * Returns the date on which the user was first detected to have used Kotlin and Gradle.
     * Returns null if an error occurred, or the user has not used it before.
     *
     * Note: This data only started being tracked in 2024, so that is the minimum date that exists and can be returned from here.
     */
    internal fun getFirstKotlinGradleUsageDate(): LocalDate? {
        if (state.firstKotlinGradleBuildTime == 0L) return null
        return Instant.ofEpochSecond(state.firstKotlinGradleBuildTime).atOffset(ZoneOffset.UTC).toLocalDate()
    }

    /**
     * Returns the date on which the user was first detected to have used Gradle (with or without Kotlin).
     * Returns null if an error occurred, or the user has not used it before.
     *
     * Note: This data only started being tracked in 2025, we use the [getFirstKotlinGradleUsageDate] date as a baseline
     * if it happened before the first recorded Gradle build.
     */
    internal fun getFirstGradleUsageDate(): LocalDate? {
        if (state.firstKotlinGradleBuildTime > 0L && state.firstKotlinGradleBuildTime < state.firstGradleBuildTime) return getFirstKotlinGradleUsageDate()
        if (state.firstGradleBuildTime == 0L) return null
        return Instant.ofEpochSecond(state.firstGradleBuildTime).atOffset(ZoneOffset.UTC).toLocalDate()
    }

    internal fun shouldShowDialog(): Boolean {
        val state = currentState
        if (state.userSawSurvey || state.firstKotlinGradleBuildTime == 0L || state.daysWithGradleBuilds < MINIMUM_BUILDS_BEFORE_SURVEY) return false
        return Duration.between(
            Instant.ofEpochSecond(state.firstKotlinGradleBuildTime),
            Instant.now()
        ) >= MINIMUM_DURATION_SINCE_FIRST_BUILD
    }
}