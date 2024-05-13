// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package onboarding.gradle

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.jetbrains.kotlin.onboarding.gradle.BuildProcessSatisfactionSurveyStore
import org.jetbrains.kotlin.onboarding.gradle.BuildProcessSatisfactionSurveyStore.Companion.MINIMUM_BUILDS_BEFORE_SURVEY
import org.jetbrains.kotlin.onboarding.gradle.BuildProcessSatisfactionSurveyStore.Companion.MINIMUM_DURATION_SINCE_FIRST_BUILD
import java.time.Instant
import kotlin.math.absoluteValue
import kotlin.test.assertNotEquals

class BuildProcessSatisfactionSurveyStoreTest : LightJavaCodeInsightFixtureTestCase() {
    private fun createInstance(): BuildProcessSatisfactionSurveyStore {
        return BuildProcessSatisfactionSurveyStore()
    }

    private fun createStateThatShouldShow(): BuildProcessSatisfactionSurveyStore {
        val instance = createInstance()
        val state = instance.currentState
        state.daysWithGradleBuilds = 100
        state.firstKotlinGradleBuildTime = (Instant.now() - MINIMUM_DURATION_SINCE_FIRST_BUILD).epochSecond - 1L
        state.lastKotlinGradleBuildTime = Instant.now().epochSecond
        state.userSawSurvey = false
        return instance
    }

    fun testEmptyState() {
        val instance = createInstance()
        assertFalse(instance.currentState.userSawSurvey)
        assertEquals(0L, instance.currentState.firstKotlinGradleBuildTime)
        assertEquals(0L, instance.currentState.lastKotlinGradleBuildTime)
        assertEquals(0L, instance.currentState.nextCountedGradleBuild)
        assertEquals(0, instance.currentState.daysWithGradleBuilds)
        assertFalse(instance.shouldShowDialog())
    }

    fun testCorrectRecordBuildBehavior() {
        val instance = createInstance()
        val currentTime = Instant.now()
        // First record, change things
        instance.recordBuild()
        val nextCountedBuild = instance.currentState.nextCountedGradleBuild
        val timeUntilNextCountedBuild = nextCountedBuild - currentTime.epochSecond
        assertTrue((timeUntilNextCountedBuild - 3600 * 24).absoluteValue < 60L)
        assertEquals(1, instance.currentState.daysWithGradleBuilds)
        assertNotEquals(0L, instance.currentState.firstKotlinGradleBuildTime)
        assertEquals(instance.currentState.firstKotlinGradleBuildTime, instance.currentState.lastKotlinGradleBuildTime)

        instance.currentState.lastKotlinGradleBuildTime = 1L
        instance.currentState.firstKotlinGradleBuildTime = 1L
        // Second record, only change the lastKotlinGradleBuildTime
        instance.recordBuild()
        assertEquals(nextCountedBuild, instance.currentState.nextCountedGradleBuild)
        assertNotEquals(1L, instance.currentState.lastKotlinGradleBuildTime)
        assertEquals(1L, instance.currentState.firstKotlinGradleBuildTime)
        instance.currentState.nextCountedGradleBuild = Instant.now().epochSecond - 60L
        instance.recordBuild()
        assertEquals(2, instance.currentState.daysWithGradleBuilds)
        val nextCountedBuild2 = instance.currentState.nextCountedGradleBuild
        val timeUntilNextCountedBuild2 = nextCountedBuild2 - currentTime.epochSecond
        assertTrue((timeUntilNextCountedBuild2 - 3600 * 24).absoluteValue < 60L)
        assertNotEquals(1L, instance.currentState.lastKotlinGradleBuildTime)
        assertEquals(1L, instance.currentState.firstKotlinGradleBuildTime)
    }

    fun testRecordDialogShown() {
        val instance = createInstance()
        instance.recordSurveyShown()
        assertTrue(instance.currentState.userSawSurvey)
    }

    fun testShouldShowIfEverythingIsFulfilled() {
        assertTrue(createStateThatShouldShow().shouldShowDialog())
    }

    fun testFirstBuildTooRecent() {
        val instance = createInstance()
        instance.currentState.firstKotlinGradleBuildTime = (Instant.now() - MINIMUM_DURATION_SINCE_FIRST_BUILD).epochSecond + 60L
        assertFalse(instance.shouldShowDialog())
    }

    fun testDialogWasAlreadyShown() {
        val instance = createInstance()
        instance.currentState.userSawSurvey = true
        assertFalse(instance.shouldShowDialog())
    }

    fun testBuildCountTooLow() {
        val instance = createInstance()
        instance.currentState.daysWithGradleBuilds = MINIMUM_BUILDS_BEFORE_SURVEY - 1
        assertFalse(instance.shouldShowDialog())
    }
}