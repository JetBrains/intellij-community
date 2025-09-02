// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.configuration

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.kotlin.idea.preferences.KotlinPreferencesBundle
import javax.swing.JComponent

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
        return panel {
            experimentalFeaturesPanel?.let { experimentalFeaturesPanel ->
                group(KotlinPreferencesBundle.message("experimental.features")) {
                    row {
                        cell(experimentalFeaturesPanel)
                    }
                }
            }
        }
    }
}