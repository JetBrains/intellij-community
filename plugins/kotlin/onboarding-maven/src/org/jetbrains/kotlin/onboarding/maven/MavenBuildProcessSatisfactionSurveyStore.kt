// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.onboarding.maven

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

internal class MavenBuildProcessSatisfactionSurveyState : BaseState() {
  var daysWithMavenBuilds by property(0)
  var userSawSurvey by property(false)
  // Unix time seconds
  var firstKotlinMavenBuildTime by property(0L)
  // Not just Kotlin but also counts Java
  var firstMavenBuildTime by property(0L)
  var lastKotlinMavenBuildTime by property(0L)
  var nextCountedMavenBuild by property(0L)
}

internal fun Project.isKotlinMavenProject(): Boolean {
  return modules.any {
    it.buildSystemType == BuildSystemType.Maven && it.hasKotlinPluginEnabled()
  }
}

@ApiStatus.Internal
@State(name = "MavenBuildProcessSatisfactionSurveyStore", storages = [Storage(value = "kotlin-onboarding.xml", roamingType = RoamingType.DISABLED)])
internal class MavenBuildProcessSatisfactionSurveyStore : PersistentStateComponent<MavenBuildProcessSatisfactionSurveyState> {
  override fun getState(): MavenBuildProcessSatisfactionSurveyState = currentState

  companion object {
    internal const val MINIMUM_BUILDS_BEFORE_SURVEY = 3
    internal val MINIMUM_DURATION_SINCE_FIRST_BUILD: Duration = Duration.ofDays(14)

    fun getInstance(): MavenBuildProcessSatisfactionSurveyStore {
      return service()
    }
  }

  internal var currentState: MavenBuildProcessSatisfactionSurveyState = MavenBuildProcessSatisfactionSurveyState()

  override fun loadState(state: MavenBuildProcessSatisfactionSurveyState) {
    currentState = state
  }

  internal fun recordSurveyShown() {
    state.userSawSurvey = true
  }

  internal fun recordNonKotlinBuild() {
    val currentTime = Instant.now()
    if (currentState.firstMavenBuildTime == 0L) {
      currentState.firstMavenBuildTime = currentTime.epochSecond
    }
  }

  internal fun recordKotlinBuild() {
    val currentTime = Instant.now()
    if (currentTime >= Instant.ofEpochSecond(currentState.nextCountedMavenBuild)) {
      currentState.daysWithMavenBuilds++
      currentState.nextCountedMavenBuild = (currentTime + Duration.ofDays(1)).epochSecond
    }
    currentState.lastKotlinMavenBuildTime = currentTime.epochSecond
    if (currentState.firstKotlinMavenBuildTime == 0L) {
      currentState.firstKotlinMavenBuildTime = currentTime.epochSecond
    }
    if (currentState.firstMavenBuildTime == 0L) {
      currentState.firstMavenBuildTime = currentTime.epochSecond
    }
  }

  /**
   * Returns the date on which the user was first detected to have used Kotlin and Maven.
   * Returns null if an error occurred, or the user has not used it before.
   */
  internal fun getFirstKotlinMavenUsageDate(): LocalDate? {
    if (state.firstKotlinMavenBuildTime == 0L) return null
    return Instant.ofEpochSecond(state.firstKotlinMavenBuildTime).atOffset(ZoneOffset.UTC).toLocalDate()
  }

  /**
   * Returns the date on which the user was first detected to have used Maven (with or without Kotlin).
   * Returns null if an error occurred, or the user has not used it before.
   */
  internal fun getFirstMavenUsageDate(): LocalDate? {
    if (state.firstKotlinMavenBuildTime > 0L && state.firstKotlinMavenBuildTime < state.firstMavenBuildTime) return getFirstKotlinMavenUsageDate()
    if (state.firstMavenBuildTime == 0L) return null
    return Instant.ofEpochSecond(state.firstMavenBuildTime).atOffset(ZoneOffset.UTC).toLocalDate()
  }

  internal fun shouldShowDialog(): Boolean {
    val state = currentState
    if (state.userSawSurvey || state.firstKotlinMavenBuildTime == 0L || state.daysWithMavenBuilds < MINIMUM_BUILDS_BEFORE_SURVEY) return false
    return Duration.between(
      Instant.ofEpochSecond(state.firstKotlinMavenBuildTime),
      Instant.now()
    ) >= MINIMUM_DURATION_SINCE_FIRST_BUILD
  }
}
