// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.tools.model.updater

import org.jetbrains.tools.model.updater.GeneratorPreferences.ApplicationMode
import org.jetbrains.tools.model.updater.impl.Preferences
import java.util.*

class GeneratorPreferences(properties: Properties) : Preferences(properties) {
    val jpsPluginVersion: String by MandatoryPreference
    val jpsPluginArtifactsMode: ArtifactMode by MandatoryPreference(ArtifactMode::valueOf)

    val kotlincVersion: String by MandatoryPreference
    val kotlinGradlePluginVersion: String by MandatoryPreference
    val kotlincArtifactsMode: ArtifactMode by MandatoryPreference(ArtifactMode::valueOf)

    val applicationMode: ApplicationMode? by OptionalPreference(ApplicationMode::valueOf)

    val kotlinCompilerRepoPath: String by MandatoryPreference

    /**
     * Represents modes of the application. [PROJECT_MODEL_UPDATER] is the default one.
     */
    enum class ApplicationMode {
        /**
         * Updates the project model.
         *
         * It includes tasks such as generation of compiler libraries and their checksum validation
         *
         * @see updateProjectModel
         */
        PROJECT_MODEL_UPDATER,

        /**
         * Runs the compiler artifacts publication, so they might be consumed by [PROJECT_MODEL_UPDATER]
         * mode later
         *
         * @see publishCompiler
         */
        COMPILER_PUBLICATION,
        ;
    }

    enum class ArtifactMode {
        MAVEN, BOOTSTRAP
    }

    companion object {
        private val configurationResources: List<String> = listOf(
            "/model.properties",

            // A file for overriding default settings. Has to be the last argument
            "/local.properties",
        )

        fun parse(args: Array<String>): GeneratorPreferences {
            val properties = Properties()

            val classForResources = this::class.java
            for (resourceFilePath in configurationResources) {
                val configurationFile = classForResources.getResource(resourceFilePath)
                configurationFile?.openStream()?.use { stream -> properties.load(stream) }
            }

            // Preferences passed as command line arguments override those from a configuration file
            for (arg in args.flatMap { it.split(" ") }) {
                val parts = arg.split('=')
                if (parts.size != 2) {
                    exitWithErrorMessage("Invalid argument: $arg")
                }

                properties[parts[0]] = parts[1]
            }

            println("Preferences:")
            properties.forEach { (k, v) -> println("$k=$v") }
            println()

            return GeneratorPreferences(properties)
        }
    }
}

fun main(args: Array<String>) {
    val preferences = GeneratorPreferences.parse(args)
    when (preferences.applicationMode) {
        null, ApplicationMode.PROJECT_MODEL_UPDATER -> updateProjectModel(preferences)
        ApplicationMode.COMPILER_PUBLICATION -> publishCompiler(preferences)
    }
}
