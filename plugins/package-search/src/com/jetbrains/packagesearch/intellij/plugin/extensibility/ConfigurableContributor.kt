package com.jetbrains.packagesearch.intellij.plugin.extensibility

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.util.ui.FormBuilder

interface ConfigurableContributor {

    companion object {

        private val extensionPointName: ExtensionPointName<ConfigurableContributor> =
                ExtensionPointName.create("com.intellij.packagesearch.configurableContributor")

        fun extensionsForProject(project: Project): List<ConfigurableContributor> =
                extensionPointName.getExtensionList(project)
    }

    fun createDriver(): ConfigurableContributorDriver
}

interface ConfigurableContributorDriver {
    fun contributeUserInterface(builder: FormBuilder)
    fun isModified(): Boolean
    fun reset()
    fun restoreDefaults()
    fun apply()
}
