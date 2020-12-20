package com.jetbrains.packagesearch.intellij.plugin.extensions.maven.configuration.ui

import com.intellij.openapi.project.Project
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.RelativeFont
import com.intellij.ui.TitledSeparator
import com.intellij.util.ui.FormBuilder
import com.jetbrains.packagesearch.intellij.plugin.PackageSearchBundle
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ConfigurableContributor
import com.jetbrains.packagesearch.intellij.plugin.extensibility.ConfigurableContributorDriver
import com.jetbrains.packagesearch.intellij.plugin.extensions.maven.configuration.PackageSearchMavenConfigurationDefaults
import com.jetbrains.packagesearch.intellij.plugin.extensions.maven.configuration.packageSearchMavenConfigurationForProject
import javax.swing.JLabel
import javax.swing.JTextField
import javax.swing.event.DocumentEvent

class MavenConfigurableContributor(private val project: Project) : ConfigurableContributor {
    override fun createDriver() = MavenConfigurableContributorDriver(project)
}

class MavenConfigurableContributorDriver(project: Project) : ConfigurableContributorDriver {
    private var modified: Boolean = false
    private val configuration = packageSearchMavenConfigurationForProject(project)

    private val textFieldChangeListener = object : DocumentAdapter() {
        override fun textChanged(e: DocumentEvent) {
            modified = true
        }
    }

    private val mavenScopeEditor = JTextField()
        .apply {
            document.addDocumentListener(textFieldChangeListener)
        }

    override fun contributeUserInterface(builder: FormBuilder) {
        builder.addComponent(
            TitledSeparator(PackageSearchBundle.message("packagesearch.configuration.maven.title")),
            0
        )
        builder.addLabeledComponent(
            PackageSearchBundle.message("packagesearch.configuration.maven.scopes.default"),
            mavenScopeEditor
        )

        val label = JLabel(
            " ${PackageSearchBundle.message("packagesearch.configuration.maven.scopes")} " +
                PackageSearchMavenConfigurationDefaults.MavenScopes.replace(",", ", ")
        )
        builder.addComponentToRightColumn(
            RelativeFont.TINY.install(RelativeFont.ITALIC.install(label))
        )
    }

    override fun isModified(): Boolean {
        return modified
    }

    override fun reset() {
        mavenScopeEditor.text = configuration.determineDefaultMavenScope()
        modified = false
    }

    override fun restoreDefaults() {
        mavenScopeEditor.text = PackageSearchMavenConfigurationDefaults.MavenScope
        modified = true
    }

    override fun apply() {
        configuration.defaultMavenScope = mavenScopeEditor.text
    }
}
