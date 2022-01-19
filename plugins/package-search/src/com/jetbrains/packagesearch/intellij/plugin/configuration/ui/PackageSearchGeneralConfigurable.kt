package com.jetbrains.packagesearch.intellij.plugin.configuration.ui

import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.ui.TitledSeparator
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.util.ui.FormBuilder
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ConfigurableContributor
import com.jetbrains.packagesearch.intellij.plugin.fus.PackageSearchEventsLogger
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
        builder.addComponent(JLabel())
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
        modified = false
    }

    private fun restoreDefaults() {
        for (contributor in extensions) {
            contributor.restoreDefaults()
        }

        PackageSearchEventsLogger.logPreferencesRestoreDefaults()
        modified = true
    }

    override fun apply() {
        for (contributor in extensions) {
            contributor.apply()
        }
        modified = false
    }
}
