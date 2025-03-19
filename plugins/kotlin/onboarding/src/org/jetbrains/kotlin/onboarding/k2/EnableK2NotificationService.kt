// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.onboarding.k2

import com.intellij.diagnostic.VMOptions
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.kotlin.idea.KotlinIcons
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider
import org.jetbrains.kotlin.idea.base.plugin.getPluginsDependingOnKotlinPluginInK2ModeAndIncompatibleWithIt
import org.jetbrains.kotlin.idea.base.util.containsNonScriptKotlinFile
import org.jetbrains.kotlin.idea.base.util.runReadActionInSmartMode
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinJpsPluginSettings
import org.jetbrains.kotlin.idea.configuration.ui.KotlinPluginKindSwitcherController
import org.jetbrains.kotlin.idea.configuration.ui.USE_K2_PLUGIN_VM_OPTION_PREFIX
import org.jetbrains.kotlin.onboarding.FeedbackBundle
import org.jetbrains.kotlin.onboarding.k2.satisfaction.survey.K2UserTracker
import org.jetbrains.kotlin.onboarding.k2.satisfaction.survey.K2UserTrackerState
import org.jetbrains.kotlin.onboarding.k2.satisfaction.survey.K2_SINCE_NOT_DEFINED

@Service
class EnableK2NotificationService {

    private val productName: @NlsSafe String
        get() = ApplicationNamesInfo.getInstance().fullProductName

    private val applicableVersions = setOf("2024.2", "2024.3")

    fun showEnableK2Notification(project: Project, state: K2UserTrackerState) {
        // We shouldn't show this notification in the K2 mode:)
        if (KotlinPluginModeProvider.currentPluginMode == KotlinPluginMode.K2) return

        // Ignore state if testing
        if (!Registry.`is`("test.enable.k2.notification", false)) {
            // We show this notification only once
            if (state.userSawEnableK2Notification) return
            // The user shouldn't have tried the K2 mode yet
            if (state.k2UserSince != K2_SINCE_NOT_DEFINED) return
        }

        // They should use Kotlin compiler 2.0 and higher
        if (!KotlinJpsPluginSettings.jpsVersion(project).startsWith("2.0")) return // TODO probably better to parse it properly

        // Show in 2024.2, 2024.3 – EAPs or internal/Nightly
        val ideShortVersion = ApplicationInfo.getInstance().shortVersion
        if (!applicableVersions.contains(ideShortVersion) ||
            (ideShortVersion.equals("2024.3") &&
                    (!ApplicationInfo.getInstance().isEAP || !ApplicationManager.getApplication().isInternal))
        ) return

        val projectContainsNonScriptKotlinFile = project.runReadActionInSmartMode {
            project.containsNonScriptKotlinFile()
        }
        if (!projectContainsNonScriptKotlinFile) return

        // Have incompatible 3rd party plugins enabled
        if (hasIncompatibleWithK2ModeThirdPartyPluginsEnabled()) return

        showNotification(project, state)
    }

    private fun hasIncompatibleWithK2ModeThirdPartyPluginsEnabled(): Boolean {
        return getPluginsDependingOnKotlinPluginInK2ModeAndIncompatibleWithIt()
            .any { !it.isBundled && it.isEnabled }
        // Code for future: get all incompatible plugins:

        /*        val pluginsToSwitchOff = mutableSetOf<IdeaPluginDescriptorImpl>()
                for (plugin in allEnabledThirdPartyPlugins) {
                    if (isPluginWhichDependsOnKotlinPluginInK2ModeAndItDoesNotSupportK2Mode(plugin, shouldCheckIfK2modeIsOn = false)) {
                        pluginsToSwitchOff.add(plugin)
                    }
                }

                if (pluginsToSwitchOff.isNotEmpty()) {
                    // For future: add to the notification that they will be switched off and switch them off:)
                    return true
                }
                return false*/
    }

    private fun showNotification(project: Project, state: K2UserTrackerState) {
        val notification = NotificationGroupManager.getInstance()
            .getNotificationGroup("Configure K2")
            .createNotification(
                title = FeedbackBundle.message("enable.k2.mode"),
                content = FeedbackBundle.message("enable.k2.mode.notification.text"),
                type = NotificationType.INFORMATION,
            ).addAction(NotificationAction.createExpiring(FeedbackBundle.message("enable.action.name")) { _, _ ->
                VMOptions.setOption(USE_K2_PLUGIN_VM_OPTION_PREFIX, true.toString())
                KotlinPluginKindSwitcherController.suggestRestart(productName)
            })
            .setIcon(KotlinIcons.SMALL_LOGO)
        notification.notify(project)

        state.userSawEnableK2Notification = true
    }

    companion object {

        fun getInstance(): K2UserTracker {
            return service()
        }
    }
}