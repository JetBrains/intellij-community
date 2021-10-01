package com.jetbrains.packagesearch.intellij.plugin.extensibility

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.util.ui.FormBuilder

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

/**
 * Provides an entrypoint to add custom UI to the settings panel under Package Search entry.
 */
interface ConfigurableContributorDriver {

    /**
     * Invoked with a [builder] to use to build the interface. Use [builder] to add custom UI to the settings panel.
     */
    fun contributeUserInterface(builder: FormBuilder)

    /**
     * Checks if the users has modified some settings.
     */
    fun isModified(): Boolean

    /**
     * Resets the settings to a state before the user has modified any of them.
     */
    fun reset()

    /**
     * Restores defaults settings.
     */
    fun restoreDefaults()

    /**
     * Applies all changes made by the user.
     */
    fun apply()
}
