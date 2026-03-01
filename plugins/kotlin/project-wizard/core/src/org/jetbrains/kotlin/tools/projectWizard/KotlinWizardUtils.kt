// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.tools.projectWizard

import com.intellij.ide.BrowserUtil
import com.intellij.ide.plugins.PluginManagerConfigurable
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.projectWizard.NewProjectWizardCollector.Kotlin.logKmpWizardInstallKmpPluginClicked
import com.intellij.ide.projectWizard.NewProjectWizardCollector.Kotlin.logKmpWizardOpenKmpPluginClicked
import com.intellij.ide.projectWizard.NewProjectWizardCollector.Kotlin.logKmpWizardWebClicked
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.TopGap
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
fun NewProjectWizardStep.addMultiPlatformLink(builder: Panel) {
    builder.row {
        if (isKmpPluginEnabled) comment(
            KotlinNewProjectWizardBundle.message("multiplatform.web.wizard.comment.click.here")
        ) {
            logKmpWizardOpenKmpPluginClicked()

            context.requestSwitchTo(KotlinMultiplatformWizardPlaceId)
        }
        else comment(
            KotlinNewProjectWizardBundle.message(
                "multiplatform.web.wizard.comment.web.wizard.or.install",
                KotlinNewProjectWizardBundle.message("multiplatform.web.wizard.link"),
            )
        ) { event ->
            val url = event.url
            if (url != null) { // web wizard link
                logKmpWizardWebClicked()

                BrowserUtil.browse(url)
            } else { // install plugin from marketplace action
                logKmpWizardInstallKmpPluginClicked()

                ShowSettingsUtil.getInstance().showSettingsDialog(null, PluginManagerConfigurable::class.java) {
                    it.openMarketplaceTab(KotlinMultiplatformPluginId)
                }
            }
        }
    }.topGap(TopGap.SMALL)
}

private val isKmpPluginEnabled: Boolean
    get() {
        val pluginId = PluginId.getId(KotlinMultiplatformPluginId)
        return PluginManagerCore.isLoaded(pluginId) && !PluginManagerCore.isDisabled(pluginId)
    }

private const val KotlinMultiplatformPluginId: String = "Kotlin Multiplatform"

private const val KotlinMultiplatformWizardPlaceId: String = "Kotlin Multiplatform"
