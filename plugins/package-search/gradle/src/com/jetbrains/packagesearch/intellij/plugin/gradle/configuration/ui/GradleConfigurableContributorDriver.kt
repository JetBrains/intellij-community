package com.jetbrains.packagesearch.intellij.plugin.gradle.configuration.ui

import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.openapi.project.Project
import com.intellij.ui.RelativeFont
import com.intellij.ui.TitledSeparator
import com.intellij.util.ui.FormBuilder
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.extensibility.AnalyticsAwareConfigurableContributorDriver
import com.jetbrains.packagesearch.intellij.plugin.fus.PackageSearchEventsLogger
import com.jetbrains.packagesearch.intellij.plugin.gradle.configuration.PackageSearchGradleConfiguration
import com.jetbrains.packagesearch.intellij.plugin.gradle.configuration.PackageSearchGradleConfigurationDefaults
import com.jetbrains.packagesearch.intellij.plugin.ui.PackageSearchUI
import com.jetbrains.packagesearch.intellij.plugin.ui.util.addOnTextChangedListener
import java.awt.event.ItemEvent

internal class GradleConfigurableContributorDriver(project: Project) : AnalyticsAwareConfigurableContributorDriver {

    private var isScopesModified: Boolean = false
    private var isDefaultScopeModified: Boolean = false
    private var isUpdateScopesModified: Boolean = false

    private val configuration = PackageSearchGradleConfiguration.getInstance(project)

    private val gradleScopesEditor = PackageSearchUI.textField {
        addOnTextChangedListener {
            isScopesModified = text.normalizeScopesList() != configuration.gradleScopes
        }
    }

    private val gradleDefaultScopeEditor = PackageSearchUI.textField {
        addOnTextChangedListener {
            isDefaultScopeModified = text.trim() != configuration.defaultGradleScope
        }
    }

    private val updateScopesOnUsageEditor =
        PackageSearchUI.checkBox(PackageSearchBundle.message("packagesearch.configuration.update.scopes.on.usage")) {
            addItemListener {
                val newIsSelected = it.stateChange == ItemEvent.SELECTED
                isUpdateScopesModified = newIsSelected != configuration.updateScopesOnUsage
            }
        }

    override fun contributeUserInterface(builder: FormBuilder) {
        // Gradle configurations
        builder.addComponent(
            TitledSeparator(PackageSearchBundle.message("packagesearch.configuration.gradle.title")),
            0
        )
        builder.addLabeledComponent(
            PackageSearchBundle.message("packagesearch.configuration.gradle.configurations"),
            gradleScopesEditor
        )
        builder.addComponentToRightColumn(
            RelativeFont.TINY.install(
                RelativeFont.ITALIC.install(
                    PackageSearchUI.createLabel(PackageSearchBundle.message("packagesearch.configuration.gradle.configurations.comma.separated"))
                )
            )
        )
        builder.addComponentToRightColumn(updateScopesOnUsageEditor)
        builder.addLabeledComponent(
            PackageSearchBundle.message("packagesearch.configuration.gradle.configurations.default"),
            gradleDefaultScopeEditor
        )
    }

    override fun isModified(): Boolean = isScopesModified || isDefaultScopeModified || isUpdateScopesModified

    override fun reset() {
        gradleScopesEditor.text = configuration.getGradleScopes().joinToString(", ")
        updateScopesOnUsageEditor.isSelected = configuration.updateScopesOnUsage
        gradleDefaultScopeEditor.text = configuration.determineDefaultGradleScope()

        isScopesModified = false
        isDefaultScopeModified = false
        isUpdateScopesModified = false
    }

    override fun restoreDefaults() {
        val defaultScopesText = PackageSearchGradleConfigurationDefaults.GradleScopes.replace(",", ", ")
        isScopesModified = gradleScopesEditor.text != defaultScopesText
        gradleScopesEditor.text = defaultScopesText

        val defaultUpdateScopes = true
        isUpdateScopesModified = updateScopesOnUsageEditor.isSelected != defaultUpdateScopes
        updateScopesOnUsageEditor.isSelected = defaultUpdateScopes

        val defaultGradleDefaultScopeName = PackageSearchGradleConfigurationDefaults.GradleDefaultScope
        isDefaultScopeModified = gradleDefaultScopeEditor.text != defaultGradleDefaultScopeName
        gradleDefaultScopeEditor.text = defaultGradleDefaultScopeName
    }

    override fun apply() {
        configuration.gradleScopes = gradleScopesEditor.text.normalizeScopesList()
        configuration.updateScopesOnUsage = updateScopesOnUsageEditor.isSelected
        configuration.defaultGradleScope = gradleDefaultScopeEditor.text.trim()

        isScopesModified = false
        isDefaultScopeModified = false
        isUpdateScopesModified = false
    }

    private fun String.normalizeScopesList() =
        split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(",")

    override fun provideApplyEventAnalyticsData(): List<EventPair<*>> {
        val hasChangedDefaultScope = configuration.defaultGradleScope != PackageSearchGradleConfigurationDefaults.GradleDefaultScope
        return listOf(
            PackageSearchEventsLogger.preferencesGradleScopeCountField.with(configuration.getGradleScopes().size),
            PackageSearchEventsLogger.preferencesUpdateScopesOnUsageField.with(configuration.updateScopesOnUsage),
            PackageSearchEventsLogger.preferencesDefaultGradleScopeChangedField.with(hasChangedDefaultScope),
        )
    }
}
