package com.jetbrains.packagesearch.intellij.plugin.gradle.configuration.ui

import com.intellij.openapi.project.Project
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.RelativeFont
import com.intellij.ui.TitledSeparator
import com.intellij.util.ui.FormBuilder
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ConfigurableContributorDriver
import com.jetbrains.packagesearch.intellij.plugin.fus.PackageSearchEventsLogger
import com.jetbrains.packagesearch.intellij.plugin.gradle.configuration.PackageSearchGradleConfiguration
import com.jetbrains.packagesearch.intellij.plugin.gradle.configuration.PackageSearchGradleConfigurationDefaults
import javax.swing.JCheckBox
import javax.swing.JLabel
import javax.swing.JTextField
import javax.swing.event.ChangeListener
import javax.swing.event.DocumentEvent

internal class GradleConfigurableContributorDriver(private val project: Project) : ConfigurableContributorDriver {

    private var modified: Boolean = false
    private val configuration
        get() = PackageSearchGradleConfiguration.getInstance(project)

    private val textFieldChangeListener = object : DocumentAdapter() {
        override fun textChanged(e: DocumentEvent) {
            modified = true
        }
    }

    private val checkboxFieldChangeListener = ChangeListener { modified = true }

    private val gradleScopesEditor = JTextField()
        .apply {
            document.addDocumentListener(textFieldChangeListener)
        }

    private val gradleDefaultScopeEditor = JTextField()
        .apply {
            document.addDocumentListener(textFieldChangeListener)
        }

    private val updateScopesOnUsageEditor =
        JCheckBox(PackageSearchBundle.message("packagesearch.configuration.update.scopes.on.usage"))
            .apply {
                addChangeListener(checkboxFieldChangeListener)
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
                    JLabel(" " + PackageSearchBundle.message("packagesearch.configuration.gradle.configurations.comma.separated"))
                )
            )
        )
        builder.addComponentToRightColumn(updateScopesOnUsageEditor)
        builder.addLabeledComponent(
            PackageSearchBundle.message("packagesearch.configuration.gradle.configurations.default"),
            gradleDefaultScopeEditor
        )
    }

    override fun isModified(): Boolean {
        return modified
    }

    override fun reset() {
        gradleScopesEditor.text = configuration.getGradleScopes().joinToString(", ")
        updateScopesOnUsageEditor.isSelected = configuration.updateScopesOnUsage
        gradleDefaultScopeEditor.text = configuration.determineDefaultGradleScope()

        modified = false
    }

    override fun restoreDefaults() {
        gradleScopesEditor.text = PackageSearchGradleConfigurationDefaults.GradleScopes.replace(",", ", ")
        updateScopesOnUsageEditor.isSelected = true
        gradleDefaultScopeEditor.text = PackageSearchGradleConfigurationDefaults.GradleDefaultScope

        modified = true
    }

    override fun apply() {
        configuration.gradleScopes = gradleScopesEditor.text.replace(", ", ",")
        configuration.updateScopesOnUsage = updateScopesOnUsageEditor.isSelected
        configuration.defaultGradleScope = gradleDefaultScopeEditor.text

        val hasChangedDefaultScope = configuration.defaultGradleScope != PackageSearchGradleConfigurationDefaults.GradleDefaultScope
        PackageSearchEventsLogger.logPreferencesChanged(
            PackageSearchEventsLogger.preferencesGradleScopeCountField.with(configuration.getGradleScopes().size),
            PackageSearchEventsLogger.preferencesUpdateScopesOnUsageField.with(configuration.updateScopesOnUsage),
            PackageSearchEventsLogger.preferencesDefaultGradleScopeChangedField.with(hasChangedDefaultScope),
        )
    }
}
