// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package onboarding.k2.satisfaction.survey

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.jetbrains.kotlin.onboarding.k2.satisfaction.survey.K2UserTracker
import java.time.Instant

class K2UserTrackerTest: LightJavaCodeInsightFixtureTestCase() {

    private fun createInstance(): K2UserTracker {
        val tracker = K2UserTracker()
        tracker.forUnitTests = true
        tracker.k2PluginModeForTests = true
        return tracker
    }

    fun `test the empty state should have correct values`() {
        val instance = createInstance()

        assertFalse(instance.state.switchedToK1)
        assertFalse(instance.state.userSawSurvey)
    }

    fun `test don't show dialog without explicit switch to K1`() {
        val instance = createInstance()

        assertFalse(instance.state.switchedToK1)
        assertFalse(instance.state.userSawSurvey)

        assertFalse(instance.shouldShowK2FeedbackDialog())

        assertFalse(instance.state.userSawSurvey)
    }

    fun `test show dialog when explicit switch to K1`() {
        val instance = createInstance()
        instance.state.switchedToK1 = true
        // instance.k2PluginModeForTests = false // it's not needed because the first branch with switchedToK1 should work

        assertFalse(instance.state.userSawSurvey)

        assertTrue(instance.shouldShowK2FeedbackDialog())
    }

    fun `test don't show dialog if less than one day on K2`() {
        val instance = createInstance()
        instance.state.k2UserSince = Instant.now().epochSecond.minus(1 * 60 * 60) // They've been on K2 just for 1 hour

        assertFalse(instance.state.switchedToK1)
        assertFalse(instance.state.userSawSurvey)

        assertFalse(instance.shouldShowK2FeedbackDialog())

        assertFalse(instance.state.userSawSurvey)
    }

    fun `test show dialog if more than one day on K2`() {
        val instance = createInstance()
        instance.state.k2UserSince = Instant.now().epochSecond.minus(25 * 60 * 60) // They've been on K2 for 25 hours

        assertFalse(instance.state.switchedToK1)
        assertFalse(instance.state.userSawSurvey)

        assertTrue(instance.shouldShowK2FeedbackDialog())
    }
}