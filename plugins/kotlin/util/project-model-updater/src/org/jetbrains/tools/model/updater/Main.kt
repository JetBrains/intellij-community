// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.tools.model.updater

import org.jetbrains.tools.model.updater.GeneratorPreferences.ApplicationMode
import org.jetbrains.tools.model.updater.impl.Preferences
import java.nio.file.Path
import java.util.Properties
import kotlin.io.path.isRegularFile

class GeneratorPreferences(properties: Properties) : Preferences(properties) {
    val jpsPluginVersion: String by MandatoryPreference
    val jpsPluginArtifactsMode: ArtifactMode by MandatoryPreference(ArtifactMode::valueOf)

    val kotlincVersion: String by MandatoryPreference
    val kotlincArtifactsMode: ArtifactMode by MandatoryPreference(ArtifactMode::valueOf)

    /**
     * KGP version used in Kotlin Gradle import tests
     * Needed as a fallback to pin the exact version of KGP in exceptional situations, otherwise intended to be absent
     *
     * @see GeneratorPreferences.kotlinGradlePluginArtifactVersion
     */
    val kotlinGradlePluginVersion: String? by OptionalPreference

    /**
     * KGP version used in Kotlin plugin tests, which rely on Gradle import
     * Intended to be fixed and updated manually because tests are included in quality gates
     *
     * @see GeneratorPreferences.kotlinNativeArtifactVersion
     */
    val kotlinNativeVersion: String? by OptionalPreference

    /**
     * YouTrack ticket for performing kt-master/master merge
     *
     * https://youtrack.jetbrains.com/articles/KTIJ-A-40/kt-master-merge-process
     */
    val ticket: String? by OptionalPreference

    /**
     * The new version of the compiler to be used in the project.
     *
     * @see ApplicationMode.ADVANCE_COMPILER_VERSION
     */
    val newKotlincVersion: String? by OptionalPreference

    val applicationMode: ApplicationMode? by OptionalPreference(ApplicationMode::valueOf)

    /**
     * A path to the Kotlin compiler repository. It can be either a relative or an absolute path. `.` points to the root of this project
     */
    val kotlinCompilerRepoPath: String? by OptionalPreference

    /**
     * Whether to publish the Gradle Plugin artifacts.
     *
     * @see ApplicationMode.COMPILER_PUBLICATION
     */
    val publishGradlePlugin: Boolean? by OptionalPreference(String::toBooleanStrictOrNull)

    /**
     * Whether to convert the JPS project to Bazel format.
     *
     * @see ApplicationMode.PROJECT_MODEL_UPDATER
     */
    val convertJpsToBazel: Boolean? by OptionalPreference(String::toBooleanStrictOrNull)

    /**
     * Whether to patch the `.patronus/config.yaml` file, excluding triggers for checks mentioned in `resources/patronus-deny-list.txt`.
     */
    val applyPatronusDenyList: Boolean? by OptionalPreference(String::toBooleanStrictOrNull)

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

        /**
         * Advances the compiler version in the project.
         *
         * ### Usage
         *
         * Provide [newKotlincVersion] preference, or it will be requested interactively
         *
         * @see advanceCompilerVersion
         */
        ADVANCE_COMPILER_VERSION,

        /**
         * Switches the Kotlin compiler in the project to the bootstrap version.
         *
         * @see switchToBootstrapKotlinCompiler
         */
        SWITCH_TO_BOOTSTRAP,
        ;
    }

    enum class ArtifactMode {
        /** Main Maven repository with Kotlin artifacts for IntelliJ IDEA. */
        MAVEN,

        /** Experimental Maven repository with artifacts from Kotlin 'master'. */
        MAVEN_EXPERIMENTAL,

        /** 'lib/kotlin-snapshot' in Community for 'kt-master' cooperative development mode. */
        BOOTSTRAP
    }

    companion object {
        internal val modelPropertiesPath: Path
            get() = KotlinTestsDependenciesUtil.communityRoot
                .resolve("plugins/kotlin/util/project-model-updater/resources/model.properties")
                .also {
                    if (!it.isRegularFile()) {
                        error("Model properties file does not exist or is not a file: $it")
                    }
                }

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
        ApplicationMode.ADVANCE_COMPILER_VERSION -> advanceCompilerVersion(preferences)
        ApplicationMode.SWITCH_TO_BOOTSTRAP -> switchToBootstrapKotlinCompiler(preferences)
    }
}
