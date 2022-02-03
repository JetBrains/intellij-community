package com.jetbrains.packagesearch.intellij.plugin.extensibility

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project

/**
 * Provider interface for creating [ConfigurableContributorDriver]s.
 */
interface ConfigurableContributor {

    companion object {

        private val extensionPointName: ExtensionPointName<ConfigurableContributor> =
            ExtensionPointName.create("com.intellij.packagesearch.configurableContributor")

        /**
         * Returns all known [ConfigurableContributor] implementations.
         */
        fun extensionsForProject(project: Project): List<ConfigurableContributor> =
            extensionPointName.getExtensionList(project)
    }

    /**
     * Creates a [ConfigurableContributorDriver] that allows to modify the settings UI panel for Package Search.
     */
    fun createDriver(): ConfigurableContributorDriver
}
