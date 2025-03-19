// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.onboarding.k2.satisfaction.survey

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.feedback.impl.OnDemandFeedbackResolver
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider
import org.jetbrains.kotlin.idea.base.util.containsNonScriptKotlinFile
import org.jetbrains.kotlin.idea.base.util.runReadActionInSmartMode
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
    var userSawEnableK2Notification by property(false)
}

@State(name = "K2NewUserTracker", storages = [Storage("k2-feedback.xml")])
class K2UserTracker : PersistentStateComponent<K2UserTrackerState> {
    companion object {
        private val LOG = Logger.getInstance(K2UserTracker::class.java)

        fun getInstance(): K2UserTracker {
            return service()
        }
    }

    @ApiStatus.Internal
    fun forceShowFeedbackForm(project: Project) {
        forced = true
        OnDemandFeedbackResolver.getInstance()
            .showFeedbackNotification(K2FeedbackSurvey::class, project) {
                forced = false
            }
    }

    private var forced: Boolean = false

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

    internal fun shouldShowK2FeedbackDialog(project: Project): Boolean {
        if (forced) return true

        LOG.debug("State: ${state}")
        if (!Registry.`is`("test.k2.feedback.survey", false)) {
            if (!forUnitTests) {
                if (ApplicationManager.getApplication().isInternal) return false // Don't show in Nightly builds or in `IDEA (dev build)`
            }
            if (state.userSawSurvey) return false // We show this survey only once
        } else {
            state.userSawSurvey = false // We reset this state for manual testing to be able to see the survey more than once
        }

        val projectContainsNonScriptKotlinFile = forUnitTests || project.runReadActionInSmartMode {
            project.containsNonScriptKotlinFile()
        }
        if (!projectContainsNonScriptKotlinFile) return false

        return switchedToK1
    }
}