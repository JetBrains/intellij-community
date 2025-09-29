// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.tools.model.updater

import org.jetbrains.tools.model.updater.impl.JpsLibrary
import org.jetbrains.tools.model.updater.impl.JpsResolverSettings
import org.jetbrains.tools.model.updater.impl.Preferences
import org.jetbrains.tools.model.updater.impl.readJpsResolverSettings
import java.nio.file.Path
import java.util.*
import kotlin.io.path.*

class GeneratorPreferences(properties: Properties) : Preferences(properties) {
    val jpsPluginVersion: String by Preference()
    val jpsPluginArtifactsMode: ArtifactMode by Preference(ArtifactMode::valueOf)

    val kotlincVersion: String by Preference()
    val kotlinGradlePluginVersion: String by Preference()
    val kotlincArtifactsMode: ArtifactMode by Preference(ArtifactMode::valueOf)

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

            val classForResources = object {}::class.java
            for (resourceFilePath in configurationResources) {
                val configurationFile = classForResources.getResource(resourceFilePath)
                configurationFile?.openStream()?.use { stream -> properties.load(stream) }
            }

            // Preferences passed as command line arguments override those from a configuration file
            for (arg in args.flatMap { it.split(" ") }) {
                val parts = arg.split('=')
                if (parts.size != 2) {
                    throw IllegalArgumentException("Invalid argument: $arg")
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
    copyBootstrapArtifactsToMavenRepositoryIfExists()

    val preferences = GeneratorPreferences.parse(args)

    val communityRoot = generateSequence(Path(".").toRealPath()) { it.parent }.first {
        it.resolve(".idea").isDirectory() &&
                !it.resolve("community").isDirectory() &&
                // This file name check is needed since the project model updater might be opened as a standalone project
                it.fileName.toString() != "project-model-updater"
    }

    val monorepoRoot = communityRoot.parent.takeIf {
        // The community name check is required since the community might be placed directly inside another repo (e.g., kotlin)
        it.resolve(".idea").isDirectory() && it.resolve("community").isDirectory()
    }

    val resolverSettings = readJpsResolverSettings(communityRoot, monorepoRoot)

    val kotlinDependenciesBazelFile = communityRoot.resolve("plugins/kotlin/kotlin_test_dependencies.bzl")
    kotlinDependenciesBazelFile.writeText(
        kotlinDependenciesBazelFile.readText()
            .replace(kotlinCompilerCliVersionRegex, "kotlinCompilerCliVersion = \"${preferences.kotlincArtifactVersion}\"")
            .replace(kotlincKotlinJpsPluginTestsVersionRegex, "kotlincKotlinJpsPluginTestsVersion = \"${preferences.jpsArtifactVersion}\"")
    )

    KotlinTestsDependenciesUtil.updateChecksum(isUpToDateCheck = false)

    fun processRoot(root: Path, isCommunity: Boolean) {
        println("Processing kotlinc libraries in root: $root")
        val libraries = generateKotlincLibraries(preferences, isCommunity)
        regenerateProjectLibraries(root.resolve(".idea"), libraries, resolverSettings)
    }

    if (monorepoRoot != null) {
        processRoot(monorepoRoot, isCommunity = false)
    }

    processRoot(communityRoot, isCommunity = true)
    updateLatestGradlePluginVersion(communityRoot, preferences.kotlinGradlePluginVersion)
    updateKGPVersionForKotlinNativeTests(communityRoot, preferences.kotlinGradlePluginVersion)
    updateCoopRunConfiguration(monorepoRoot, communityRoot)
}

private fun regenerateProjectLibraries(dotIdea: Path, newLibraries: List<JpsLibrary>, resolverSettings: JpsResolverSettings) {
    val librariesDir = dotIdea.resolve("libraries")
    val oldLibraries = librariesDir.listDirectoryEntries("kotlinc_*").toMutableSet()

    for (library in newLibraries) {
        val libraryFileName = library.name.replace("\\W".toRegex(), "_") + ".xml"
        val xmlFile = librariesDir.resolve(libraryFileName)
        val isRegeneration = oldLibraries.remove(xmlFile)
        if (isRegeneration) {
            println("Rewriting $xmlFile")
        } else {
            println("Writing $xmlFile")
        }

        xmlFile.writeText(library.render(resolverSettings))
    }

    // Drop redundant libraries
    for (redundantLibrary in oldLibraries) {
        println("Removing $redundantLibrary")
        redundantLibrary.deleteExisting()
    }
}

private fun updateCoopRunConfiguration(monorepoRoot: Path?, communityRoot: Path) {
    val runConfigurationFilePath = ".idea/runConfigurations/Kotlin_Coop__Publish_compiler_for_ide_JARs.xml"
    val communityRunConfigurationFile = communityRoot.resolve(runConfigurationFilePath)
    val originalText = communityRunConfigurationFile.readText()

    val communityResultText = originalText.replace(bootstrapVersionRegex) { matchResult ->
        "${matchResult.groupValues[1]}$BOOTSTRAP_VERSION${matchResult.groupValues[4]}"
    }

    communityRunConfigurationFile.writeText(communityResultText)

    val monorepoResultText = communityResultText.replace(Regex.fromLiteral("-Pkotlin.build.deploy-path=intellij/")) { matchResult ->
        matchResult.groupValues[0] + "community/"
    }

    require(monorepoResultText != communityResultText)

    val monorepoRunConfigurationFile = monorepoRoot?.resolve(runConfigurationFilePath)
    monorepoRunConfigurationFile?.writeText(monorepoResultText)
}

private val bootstrapVersionRegex = Regex("(-P(deployVersion|build\\.number)=)(.+?)([ \"])")

internal val kotlinCompilerCliVersionRegex: Regex = Regex("""kotlinCompilerCliVersion\s*=\s*"(\S+)"""")
internal val kotlincKotlinJpsPluginTestsVersionRegex: Regex = Regex("""kotlincKotlinJpsPluginTestsVersion\s*=\s*"(\S+)"""")

/**
 * Updates the `KotlinGradlePluginVersions.kt` source file to contain the latest [kotlinGradlePluginVersion]
 * in the source code. The `KotlinGradlePluginVersions` source file can't directly read the `model.properties` file directly, since
 * the project model can be overwritten by the [main] args (see also [GeneratorPreferences.parse])
 */
private fun updateLatestGradlePluginVersion(communityRoot: Path, kotlinGradlePluginVersion: String) {
    val kotlinGradlePluginVersionsKt = communityRoot.resolve(
        "plugins/kotlin/gradle/gradle-java/tests.shared/test/org/jetbrains/kotlin/idea/codeInsight/gradle/KotlinGradlePluginVersions.kt"
    )

    updateFile(
        kotlinGradlePluginVersionsKt,
        """val latest = .*""",
        "val latest = KotlinToolingVersion(\"$kotlinGradlePluginVersion\")",
    )
}

private fun updateKGPVersionForKotlinNativeTests(communityRoot: Path, kotlinGradlePluginVersion: String) {
    val kotlinNativeVersionsKt = communityRoot.resolve(
        "plugins/kotlin/base/plugin/test/org/jetbrains/kotlin/idea/artifacts/KotlinNativeVersion.kt"
    )
    updateFile(
        kotlinNativeVersionsKt,
        """private const val kotlinGradlePluginVersion: String =.*""",
        "private const val kotlinGradlePluginVersion: String = \"$kotlinGradlePluginVersion\"",
    )
}

private fun updateFile(sourceFile: Path, regexp: String, replacement: String) {
    val updatedFileContent = sourceFile.readText().replace(
        Regex(regexp), replacement
    )

    sourceFile.writeText(updatedFileContent)
}
