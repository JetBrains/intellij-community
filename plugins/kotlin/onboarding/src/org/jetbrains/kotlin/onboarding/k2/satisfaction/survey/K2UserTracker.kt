// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.onboarding.k2.satisfaction.survey

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider
import java.time.Duration
import java.time.Instant

internal const val K2_SINCE_NOT_DEFINED = -1L

internal enum class PluginModes(val value: String) {
    UNDEFINED("Undefined"),
    K1("K1"),
    K2("K2")
}

class K2UserTrackerState : BaseState() {

    // Unix time seconds or -1 if not defined
    var k2UserSince by property(K2_SINCE_NOT_DEFINED)
    /* We need to store that the user saw the survey because the state in com.intellij.platform.feedback.impl.state.CommonFeedbackSurveyService
    doesn't migrate when updating the IDE */
    var userSawSurvey by property(false)
    var lastSavedPluginMode by string(PluginModes.UNDEFINED.value)
}

@State(name = "K2NewUserTracker", storages = [Storage("k2-feedback.xml")])
class K2UserTracker : PersistentStateComponent<K2UserTrackerState> {
    companion object {
        private val LOG = Logger.getInstance(K2UserTracker::class.java)

        fun getInstance(): K2UserTracker {
            return service()
        }
    }

    internal var switchedToK1 = false
    internal var forUnitTests = false
    internal var k2PluginModeForTests = false // Want the test to not depend on a real K1/K2 mode

    internal var currentState = K2UserTrackerState()

    override fun getState(): K2UserTrackerState = currentState

    override fun loadState(state: K2UserTrackerState) {
        currentState = state
    }

    fun checkIfKotlinPluginModeWasSwitchedOnRestart() {
        // The user has just switched to K1
        if (KotlinPluginModeProvider.currentPluginMode == KotlinPluginMode.K1 && state.lastSavedPluginMode == PluginModes.K2.value) {
            LOG.debug("Switched to K1")
            switchedToK1 = true
        }
        // The user has just switched to K2
        if (KotlinPluginModeProvider.currentPluginMode == KotlinPluginMode.K2 && state.lastSavedPluginMode == PluginModes.K1.value) {
            LOG.debug("Switched to K2")
            state.k2UserSince = Instant.now().epochSecond
        }
        state.lastSavedPluginMode = KotlinPluginModeProvider.currentPluginMode.name
    }

    internal fun shouldShowK2FeedbackDialog(): Boolean {
        LOG.debug("State: ${state}")
        if (!Registry.`is`("test.k2.feedback.survey", false)) {
            if (!forUnitTests) {
                if (ApplicationManager.getApplication().isInternal) return false // Don't show in Nightly builds or in `IDEA (dev build)`
            }
            if (state.userSawSurvey) return false // We show this survey only once
        } else {
            state.userSawSurvey = false // We reset this state for manual testing to be able to see the survey more than once
        }
        if (switchedToK1) {
            return true
        } else {
            val k2Chosen = if (forUnitTests) {
                k2PluginModeForTests
            } else {
                KotlinPluginModeProvider.currentPluginMode == KotlinPluginMode.K2
            }
            if (!k2Chosen) {
                LOG.debug("Not showing the K2 feedback dialog because the user doesn't use K2")
                return false
            } else {
                // The following condition is needed if a user had always been on K2 even before the survey started
                if (state.k2UserSince == K2_SINCE_NOT_DEFINED) {
                    state.k2UserSince = Instant.now().epochSecond
                }
                val k2UserSince = Instant.ofEpochSecond(state.k2UserSince) // k2UserSince might be initialized here on the first access
                val durationSinceK2User = Duration.between(k2UserSince, Instant.now())

                LOG.debug("Duration since user became a K2 Kotlin user: ${durationSinceK2User.toDays()} day(s)")
                return durationSinceK2User > Duration.ofSeconds(
                        Registry.intValue("minimum.usage.time.before.showing.k2.survey").toLong()
                    )
            }
        }
    }
}