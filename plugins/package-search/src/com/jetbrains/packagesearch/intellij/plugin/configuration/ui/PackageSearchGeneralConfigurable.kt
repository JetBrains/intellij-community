package com.jetbrains.packagesearch.intellij.plugin.configuration.ui

import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.ui.TitledSeparator
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.util.ui.FormBuilder
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.configuration.PackageSearchGeneralConfiguration
import com.jetbrains.packagesearch.intellij.plugin.extensibility.AnalyticsAwareConfigurableContributorDriver
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ConfigurableContributor
import com.jetbrains.packagesearch.intellij.plugin.fus.PackageSearchEventsLogger
import com.jetbrains.packagesearch.intellij.plugin.ui.PackageSearchUI
import java.awt.event.ItemEvent.SELECTED
import javax.swing.JComponent
import javax.swing.JPanel

class PackageSearchGeneralConfigurable(project: Project) : SearchableConfigurable {

    private val extensions = ConfigurableContributor.extensionsForProject(project)
        .sortedBy { it.javaClass.simpleName }
        .map { it.createDriver() }

    private val isAnyContributorModified: Boolean
        get() = extensions.any { it.isModified() }

    private var isAutoAddRepositoriesModified: Boolean = false

    private val builder = FormBuilder.createFormBuilder()

    private val configuration = PackageSearchGeneralConfiguration.getInstance(project)

    private val autoAddRepositoriesCheckBox = PackageSearchUI.checkBox(
        PackageSearchBundle.message("packagesearch.configuration.automatically.add.repositories")
    ) {
        isSelected = configuration.autoAddMissingRepositories
        addItemListener {
            val newIsSelected = it.stateChange == SELECTED
            isAutoAddRepositoriesModified = newIsSelected != configuration.autoAddMissingRepositories
        }
    }

    override fun getId(): String = ID

    override fun getDisplayName(): String = PackageSearchBundle.message("packagesearch.configuration.title")

    override fun createComponent(): JComponent? {
        // Extensions
        extensions.forEach {
            it.contributeUserInterface(builder)
        }

        // General options
        builder.addComponent(
            TitledSeparator(PackageSearchBundle.message("packagesearch.configuration.general")),
            0
        )

        // Reset defaults
        builder.addComponent(autoAddRepositoriesCheckBox)
        builder.addComponent(
            LinkLabel<Any>(
                PackageSearchBundle.message("packagesearch.configuration.restore.defaults"),
                null
            ) { _, _ -> restoreDefaults() }
        )

        builder.addComponentFillVertically(JPanel(), 0)

        return builder.panel
    }

    override fun isModified() = isAutoAddRepositoriesModified || isAnyContributorModified

    override fun reset() {
        for (contributor in extensions) {
            contributor.reset()
        }

        autoAddRepositoriesCheckBox.isSelected = configuration.autoAddMissingRepositories

        isAutoAddRepositoriesModified = false
    }

    private fun restoreDefaults() {
        for (contributor in extensions) {
            contributor.restoreDefaults()
        }

        val defaultAutoAddRepositories = true
        isAutoAddRepositoriesModified = autoAddRepositoriesCheckBox.isSelected == defaultAutoAddRepositories
        autoAddRepositoriesCheckBox.isSelected = defaultAutoAddRepositories

        PackageSearchEventsLogger.logPreferencesRestoreDefaults()
    }

    override fun apply() {
        val analyticsFields = mutableSetOf<EventPair<*>>()
        for (contributor in extensions) {
            contributor.apply()
            if (contributor is AnalyticsAwareConfigurableContributorDriver) {
                analyticsFields.addAll(contributor.provideApplyEventAnalyticsData())
            }
        }

        configuration.autoAddMissingRepositories = autoAddRepositoriesCheckBox.isSelected
        analyticsFields += PackageSearchEventsLogger.preferencesAutoAddRepositoriesField.with(configuration.autoAddMissingRepositories)
        PackageSearchEventsLogger.logPreferencesChanged(*analyticsFields.toTypedArray())

        isAutoAddRepositoriesModified = false
    }

    companion object {

        const val ID = "preferences.packagesearch.PackageSearchGeneralConfigurable"
    }
}
