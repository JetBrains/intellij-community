// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.configuration

import com.intellij.ide.ui.search.SearchableOptionContributor
import com.intellij.ide.ui.search.SearchableOptionProcessor
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.kotlin.idea.configuration.ui.KotlinPluginKindSwitcherController
import org.jetbrains.kotlin.idea.preferences.KotlinPreferencesBundle
import javax.swing.JComponent

class KotlinLanguageConfiguration : SearchableConfigurable, Configurable.NoScroll {
    companion object {
        const val ID = "preferences.language.Kotlin"
    }

    private val experimentalFeaturesPanel: ExperimentalFeaturesPanel? = ExperimentalFeaturesPanel.createPanelIfShouldBeShown()

    private val kotlinPluginKindSwitcherController: KotlinPluginKindSwitcherController? =
        KotlinPluginKindSwitcherController.createIfPluginSwitchIsPossible()

    override fun getId(): String = ID

    override fun getDisplayName(): String = KotlinPreferencesBundle.message("configuration.name.kotlin")


    override fun isModified() =
        experimentalFeaturesPanel?.isModified() == true ||
                kotlinPluginKindSwitcherController?.isModified() == true

    override fun reset() {
        super.reset()
        kotlinPluginKindSwitcherController?.reset()
    }

    override fun apply() {
        // Selected channel is now saved automatically

        experimentalFeaturesPanel?.applySelectedChanges()
        kotlinPluginKindSwitcherController?.applyChanges()
    }

    override fun createComponent(): JComponent? {
        return panel {
            kotlinPluginKindSwitcherController?.let { kotlinPluginKindSwitcherController ->
                row {
                    cell(kotlinPluginKindSwitcherController.createComponent())
                }
            }

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


class KotlinPluginSwitchSearchOptionContributor : SearchableOptionContributor() {
    override fun processOptions(processor: SearchableOptionProcessor) {
        val displayName = KotlinPreferencesBundle.message("kotlin.language.configurable")
        processor.addOptions(
            KotlinPreferencesBundle.message("checkbox.enable.k2.based.kotlin.plugin"),
            null,
            displayName,
            KotlinLanguageConfiguration.ID,
            displayName,
            false
        )
    }
}