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

internal class BuildProcessSatisfactionSurveyState : BaseState() {
    var daysWithGradleBuilds by property(0)
    var userSawSurvey by property(false)
    // Unix time seconds
    var firstKotlinGradleBuildTime by property(0L)
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

    internal fun recordBuild() {
        val currentTime = Instant.now()
        if (currentTime >= Instant.ofEpochSecond(currentState.nextCountedGradleBuild)) {
            currentState.daysWithGradleBuilds++
            currentState.nextCountedGradleBuild = (currentTime + Duration.ofDays(1)).epochSecond
        }
        currentState.lastKotlinGradleBuildTime = currentTime.epochSecond
        if (currentState.firstKotlinGradleBuildTime == 0L) {
            currentState.firstKotlinGradleBuildTime = currentTime.epochSecond
        }
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