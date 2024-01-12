// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package onboarding.k2.satisfaction.survey

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.jetbrains.kotlin.onboarding.k2.satisfaction.survey.K2UserTracker
import org.jetbrains.kotlin.onboarding.k2.satisfaction.survey.K2_SINCE_NOT_DEFINED
import org.jetbrains.kotlin.onboarding.k2.satisfaction.survey.PluginModes
import java.time.Instant

class K2UserTrackerTest: LightJavaCodeInsightFixtureTestCase() {

    private fun createInstance(): K2UserTracker {
        val tracker = K2UserTracker()
        tracker.forUnitTests = true
        return tracker
    }

    fun `test the empty state should have correct values`() {
        val instance = createInstance()

        instance.k2PluginModeForTests = false
        assert(instance.state.lastSavedPluginMode == PluginModes.UNDEFINED.value)
        assert(instance.state.k2UserSince == K2_SINCE_NOT_DEFINED)
        assertFalse(instance.state.userSawSurvey)
    }

    fun `test don't show dialog without explicit switch to K1`() {
        val instance = createInstance()

        instance.k2PluginModeForTests = false
        assert(instance.state.lastSavedPluginMode == PluginModes.UNDEFINED.value)
        instance.switchedToK1 = false
        assertFalse(instance.state.userSawSurvey)

        assertFalse(instance.shouldShowK2FeedbackDialog())

        assertFalse(instance.state.userSawSurvey)
    }

    fun `test show dialog when explicit switch to K1`() {
        val instance = createInstance()

        instance.state.lastSavedPluginMode = PluginModes.K2.value
        instance.switchedToK1 = true

        assertFalse(instance.state.userSawSurvey)

        assertTrue(instance.shouldShowK2FeedbackDialog())
    }

    fun `test don't show dialog if less than one day on K2`() {
        val instance = createInstance()
        instance.state.k2UserSince = Instant.now().epochSecond.minus(1 * 60 * 60) // They've been on K2 just for 1 hour
        instance.state.lastSavedPluginMode = PluginModes.K2.value
        instance.k2PluginModeForTests = true

        assertFalse(instance.state.userSawSurvey)

        assertFalse(instance.shouldShowK2FeedbackDialog())

        assertFalse(instance.state.userSawSurvey)
    }

    fun `test show dialog if more than one day on K2`() {
        val instance = createInstance()
        instance.state.k2UserSince = Instant.now().epochSecond.minus(25 * 60 * 60) // They've been on K2 for 25 hours
        instance.state.lastSavedPluginMode = PluginModes.K2.value
        instance.k2PluginModeForTests = true

        assertFalse(instance.state.userSawSurvey)

        assertTrue(instance.shouldShowK2FeedbackDialog())
    }
}