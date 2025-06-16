// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.configuration

import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.ide.ui.search.SearchableOptionContributor
import com.intellij.ide.ui.search.SearchableOptionProcessor
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.options.ex.Settings
import com.intellij.ui.dsl.builder.*
import com.intellij.util.ui.JBFont
import org.jetbrains.kotlin.idea.configuration.ui.KotlinPluginKindSwitcherController
import org.jetbrains.kotlin.idea.preferences.KotlinPreferencesBundle
import java.awt.Component
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

    private val kotlinPluginKindSwitcherController: KotlinPluginKindSwitcherController =
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

    override fun createComponent(): JComponent {
        return panel {
            kotlinPluginKindSwitcherController?.let { kotlinPluginKindSwitcherController ->
                row {
                    cell(kotlinPluginKindSwitcherController.createComponent())
                }

                separator()
                    .topGap(TopGap.SMALL)
                    .bottomGap(BottomGap.SMALL)

            }

            row {
                icon(AllIcons.General.Information).align(AlignY.TOP).gap(rightGap = RightGap.SMALL)
                panel {
                    row {
                        text(
                            text = KotlinPreferencesBundle.message(
                                "kotlin.plugin.is.no.longer.updated.separately.from.the.0",
                                ApplicationNamesInfo.getInstance().fullProductName,
                            ),
                            maxLineLength = DEFAULT_COMMENT_WIDTH,
                        ).applyToComponent { font = JBFont.medium() }
                    }
                    row {
                        text(
                            text = KotlinPreferencesBundle.message("check.for.ide.updates"),
                            maxLineLength = DEFAULT_COMMENT_WIDTH,
                            action = { selectUpdatesConfigurable(it.inputEvent?.component) },
                        ).applyToComponent { font = JBFont.medium() }
                    }
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

    private fun selectUpdatesConfigurable(component: Component?) {
        if (component != null) {
            Settings.KEY.getData(DataManager.getInstance().getDataContext(component))?.let { settings ->
                settings.select(settings.find("preferences.updates"))
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
            KotlinPreferencesBundle.message("checkbox.enable.k2.based.kotlin.plugin"),
            KotlinLanguageConfiguration.ID,
            displayName,
            false
        )
    }
}