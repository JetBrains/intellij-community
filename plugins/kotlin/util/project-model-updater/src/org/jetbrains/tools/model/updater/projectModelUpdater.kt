// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.tools.model.updater

import org.jetbrains.tools.model.updater.impl.JpsLibrary
import org.jetbrains.tools.model.updater.impl.JpsResolverSettings
import org.jetbrains.tools.model.updater.impl.readJpsResolverSettings
import java.nio.file.Path
import kotlin.io.path.deleteExisting
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText
import kotlin.io.path.writeText

internal fun updateProjectModel(preferences: GeneratorPreferences) {
    copyBootstrapArtifactsToMavenRepositoryIfExists()

    val communityRoot = KotlinTestsDependenciesUtil.communityRoot
    val monorepoRoot = KotlinTestsDependenciesUtil.monorepoRoot

    val resolverSettings = readJpsResolverSettings(communityRoot, monorepoRoot)

    val kotlinDependenciesBazelFile = communityRoot.resolve("plugins/kotlin/kotlin_test_dependencies.bzl")
    kotlinDependenciesBazelFile.writeText(
        kotlinDependenciesBazelFile.readText()
            .replace(kotlinCompilerCliVersionRegex, "kotlinCompilerCliVersion = \"${preferences.kotlincArtifactVersion}\"")
            .replace(kotlincKotlinJpsPluginTestsVersionRegex, "kotlincKotlinJpsPluginTestsVersion = \"${preferences.jpsArtifactVersion}\"")
    )

    KotlinTestsDependenciesUtil.updateChecksum(isUpToDateCheck = false)

    fun processRoot(root: Path, isCommunity: Boolean) {
        println("Processing kotlinc libraries in '$root'...")
        val libraries = generateKotlincLibraries(preferences, isCommunity)
        regenerateProjectLibraries(root.resolve(".idea"), libraries, resolverSettings)
    }

    if (monorepoRoot != null) {
        processRoot(monorepoRoot, isCommunity = false)
    }

    processRoot(communityRoot, isCommunity = true)
    updateLatestGradlePluginVersion(communityRoot, preferences.kotlinGradlePluginVersion)
    updateKGPVersionForKotlinNativeTests(communityRoot, preferences.kotlinGradlePluginVersion)
}

private fun regenerateProjectLibraries(dotIdea: Path, newLibraries: List<JpsLibrary>, resolverSettings: JpsResolverSettings) {
    val librariesDir = dotIdea.resolve("libraries")
    val oldLibraries = librariesDir.listDirectoryEntries("kotlinc_*").toMutableSet()

    for (library in newLibraries) {
        val libraryFileName = library.name.replace("\\W".toRegex(), "_") + ".xml"
        val xmlFile = librariesDir.resolve(libraryFileName)
        val isRegeneration = oldLibraries.remove(xmlFile)
        if (isRegeneration) {
            println("Rewriting '$xmlFile'...")
        } else {
            println("Writing '$xmlFile'...")
        }

        xmlFile.writeText(library.render(resolverSettings))
    }

    // Drop redundant libraries
    for (redundantLibrary in oldLibraries) {
        println("Removing '$redundantLibrary'...")
        redundantLibrary.deleteExisting()
    }
}

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
