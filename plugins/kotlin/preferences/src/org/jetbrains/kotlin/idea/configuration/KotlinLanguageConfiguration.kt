// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.configuration

import com.intellij.ide.DataManager
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.options.ex.Settings
import com.intellij.ui.HyperlinkAdapter
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.kotlin.idea.preferences.KotlinPreferencesBundle
import javax.swing.JComponent
import javax.swing.event.HyperlinkEvent

internal class KotlinLanguageConfiguration : SearchableConfigurable, Configurable.NoScroll {
    companion object {
        /**
         * Kotlin search configurable ID.
         *
         * Has a few hardcoded usages because of circular dependencies.
         * @see [org.jetbrains.kotlin.idea.configuration.KotlinK2FeaturesInK1ModeNotifier]
         * @see [org.jetbrains.kotlin.idea.base.fe10.highlighting.suspender.KotlinHighlightingSuspendNotificationProvider]
         */
        const val ID = "preferences.language.Kotlin"
    }

    private val experimentalFeaturesPanel: ExperimentalFeaturesPanel? = ExperimentalFeaturesPanel.createPanelIfShouldBeShown()

    override fun getId(): String = ID

    override fun getDisplayName(): String = KotlinPreferencesBundle.message("configuration.name.kotlin")


    override fun isModified() =
        experimentalFeaturesPanel?.isModified() == true

    override fun apply() {
        // Selected channel is now saved automatically

        experimentalFeaturesPanel?.applySelectedChanges()
    }

    override fun createComponent(): JComponent {
        val kotlinScriptingSettingsLink = createKotlinScriptingSettingsHyperlink()
        return panel {
            experimentalFeaturesPanel?.let { experimentalFeaturesPanel ->
                group(KotlinPreferencesBundle.message("experimental.features")) {
                    row {
                        cell(experimentalFeaturesPanel)
                    }
                }
            }
            row {
                cell(kotlinScriptingSettingsLink)
            }
        }
    }

    private fun createKotlinScriptingSettingsHyperlink(): JComponent {
        @Suppress("DialogTitleCapitalization") // It is properly capitalized
        val scriptingLink = HyperlinkLabel(KotlinPreferencesBundle.message("kotlin.scripting.configurable"))
        scriptingLink.addHyperlinkListener(object : HyperlinkAdapter() {
            override fun hyperlinkActivated(e: HyperlinkEvent) {
                val dataContext = DataManager.getInstance().getDataContext(scriptingLink)
                val settings = Settings.KEY.getData(dataContext) ?: return
                val kotlinScriptingConfigurable = settings.find(KOTLIN_SCRIPTING_SETTINGS_ID)
                settings.select(kotlinScriptingConfigurable)
            }
        })
        return scriptingLink
    }
}