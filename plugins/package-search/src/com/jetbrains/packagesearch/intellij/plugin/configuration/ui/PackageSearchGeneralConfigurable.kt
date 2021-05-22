package com.jetbrains.packagesearch.intellij.plugin.configuration.ui

import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.ui.RelativeFont
import com.intellij.ui.TitledSeparator
import com.intellij.ui.components.ActionLink
import com.intellij.util.ui.FormBuilder
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.configuration.PackageSearchGeneralConfiguration
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ConfigurableContributor
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.event.ChangeListener

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
    private val configuration = PackageSearchGeneralConfiguration.getInstance(project)

    private val checkboxFieldChangeListener = ChangeListener { modified = true }

    private val builder = FormBuilder.createFormBuilder()

    private val refreshProjectEditor =
        JCheckBox(PackageSearchBundle.message("packagesearch.configuration.refresh.project"))
            .apply {
                addChangeListener(checkboxFieldChangeListener)
            }

    private val allowCheckForPackageUpgradesEditor =
        JCheckBox(PackageSearchBundle.message("packagesearch.configuration.allow.check.upgrades"))
            .apply {
                addChangeListener(checkboxFieldChangeListener)
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

        // Refresh project?
        builder.addComponent(refreshProjectEditor)

        // Allow checking for package upgrades?
        builder.addComponent(allowCheckForPackageUpgradesEditor)
        builder.addComponent(
            RelativeFont.TINY.install(
                JLabel(PackageSearchBundle.message("packagesearch.configuration.allow.check.upgrades.extrainfo"))
            )
        )

        // Reset defaults
        builder.addComponent(JLabel())
        builder.addComponent(ActionLink(PackageSearchBundle.message("packagesearch.configuration.restore.defaults")) {
          restoreDefaults()
        })

        builder.addComponentFillVertically(JPanel(), 0)

        return builder.panel
    }

    override fun isModified() = modified || extensions.any { it.isModified() }

    override fun reset() {
        extensions.forEach {
            it.reset()
        }

        refreshProjectEditor.isSelected = configuration.refreshProject
        allowCheckForPackageUpgradesEditor.isSelected = configuration.allowCheckForPackageUpgrades

        modified = false
    }

    private fun restoreDefaults() {
        extensions.forEach {
            it.restoreDefaults()
        }

        refreshProjectEditor.isSelected = true
        allowCheckForPackageUpgradesEditor.isSelected = true

        modified = true
    }

    override fun apply() {
        extensions.forEach {
            it.apply()
        }

        configuration.refreshProject = refreshProjectEditor.isSelected
        configuration.allowCheckForPackageUpgrades = allowCheckForPackageUpgradesEditor.isSelected
    }
}
