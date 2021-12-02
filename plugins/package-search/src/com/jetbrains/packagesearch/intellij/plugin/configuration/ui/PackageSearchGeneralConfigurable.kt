package com.jetbrains.packagesearch.intellij.plugin.configuration.ui

import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.ui.TitledSeparator
import com.intellij.ui.components.CheckBox
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.util.ui.FormBuilder
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.configuration.PackageSearchGeneralConfiguration
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ConfigurableContributor
import com.jetbrains.packagesearch.intellij.plugin.fus.PackageSearchEventsLogger
import com.jetbrains.packagesearch.intellij.plugin.ui.PackageSearchUI
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class PackageSearchGeneralConfigurable(project: Project) : SearchableConfigurable {

    companion object {

        const val ID = "preferences.packagesearch.PackageSearchGeneralConfigurable"
    }

    override fun getId(): String = ID

    override fun getDisplayName(): String = PackageSearchBundle.message("packagesearch.configuration.title")

    private val extensions = ConfigurableContributor.extensionsForProject(project)
        .sortedBy { it.javaClass.simpleName }
        .map { it.createDriver() }

    private var modified: Boolean = false

    private val builder = FormBuilder.createFormBuilder()

    private val configuration = PackageSearchGeneralConfiguration.getInstance(project)

    private val autoAddRepositoriesCheckBox = PackageSearchUI.checkBox(
        PackageSearchBundle.message("packagesearch.configuration.automatically.add.repositories")
    ) {
        isSelected = configuration.autoAddMissingRepositories
    }

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

    override fun isModified() = modified || extensions.any { it.isModified() }

    override fun reset() {
        for (contributor in extensions) {
            contributor.reset()
        }

        autoAddRepositoriesCheckBox.isSelected = configuration.autoAddMissingRepositories

        modified = false
    }

    private fun restoreDefaults() {
        for (contributor in extensions) {
            contributor.restoreDefaults()
        }

        configuration.autoAddMissingRepositories = true
        autoAddRepositoriesCheckBox.isSelected = true

        PackageSearchEventsLogger.logPreferencesRestoreDefaults()
        modified = true
    }

    override fun apply() {
        for (contributor in extensions) {
            contributor.apply()
        }

        configuration.autoAddMissingRepositories = autoAddRepositoriesCheckBox.isSelected

        modified = false
    }
}
