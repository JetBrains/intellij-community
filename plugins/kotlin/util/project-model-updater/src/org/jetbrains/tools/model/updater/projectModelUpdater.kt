// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.tools.model.updater

import org.jetbrains.tools.model.updater.impl.JpsLibrary
import org.jetbrains.tools.model.updater.impl.JpsResolverSettings
import org.jetbrains.tools.model.updater.impl.readJpsResolverSettings
import org.jetbrains.tools.model.updater.impl.xml
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

    fun processRoot(root: Path, libraries: List<JpsLibrary>) {
        println("Processing kotlinc libraries in '$root'...")
        val dotIdea = root.resolve(".idea")
        regenerateProjectLibraries(dotIdea, libraries, resolverSettings)
        regenerateAnchorsXml(dotIdea, libraries)
    }

    if (monorepoRoot != null) {
        processRoot(monorepoRoot, generateKotlincLibraries(preferences, isCommunity = false))
    }

    val communityLibraries = generateKotlincLibraries(preferences, isCommunity = true)
    processRoot(communityRoot, communityLibraries)
    regenerateCompilerDependenciesIml(communityRoot, communityLibraries)
    updateLatestGradlePluginVersion(communityRoot, preferences.kotlinGradlePluginVersion)
    updateKGPVersionForKotlinNativeTests(communityRoot, preferences.kotlinGradlePluginVersion)

    if (monorepoRoot != null && preferences.convertJpsToBazel == true) {
        convertJpsToBazel(monorepoRoot)
    }
}

private fun convertJpsToBazel(monorepoRoot: Path) {
    println("Converting JPS model to Bazel...")
    val exitCode = ProcessBuilder("build/jpsModelToBazel.cmd")
        .directory(monorepoRoot.toFile())
        .inheritIO()
        .start()
        .waitFor()

    if (exitCode != 0) {
        exitWithErrorMessage("The JPS-to-Bazel converter has failed", exitCode)
    }
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

private fun regenerateCompilerDependenciesIml(communityRoot: Path, libraries: List<JpsLibrary>) {
    val imlFile = communityRoot.resolve(
        "plugins/kotlin/util/compiler-dependencies/kotlin.util.compiler-dependencies.iml"
    )
    println("Rewriting '$imlFile'...")
    imlFile.writeText(renderCompilerDependenciesIml(libraries))
}

private fun regenerateAnchorsXml(dotIdea: Path, libraries: List<JpsLibrary>) {
    val anchorsFile = dotIdea.resolve("anchors.xml")
    println("Rewriting '$anchorsFile'...")
    anchorsFile.writeText(renderAnchorsXml(libraries))
}

internal fun renderAnchorsXml(libraries: List<JpsLibrary>): String {
    val anchorModule = "kotlin.util.compiler-classpath"

    // Some non-kotlinc libraries might require anchors as well
    val hardcodedLibraries = listOf("kotlin-script-runtime")
    val libraryNames = libraries.map { it.name } + hardcodedLibraries
    return xml("project", "version" to "4") {
        xml("component", "name" to "KotlinIdeAnchorService") {
            xml("option", "name" to "moduleNameToAnchorName") {
                xml("map") {
                    for (name in libraryNames.sorted()) {
                        xml("entry", "key" to name, "value" to anchorModule)
                    }
                }
            }
        }
        xml("component", "name" to "LibraryToSourceAnalysisState") {
            xml("option", "name" to "isEnabled", "value" to "true")
        }
    }.render(addXmlDeclaration = true)
}

internal fun renderCompilerDependenciesIml(libraries: List<JpsLibrary>): String {
    return xml("module", "type" to "JAVA_MODULE", "version" to "4") {
        xml("component", "name" to "NewModuleRootManager", "inherit-compiler-output" to "true") {
            xml("exclude-output")
            xml("content", "url" to $$"file://$MODULE_DIR$")
            xml("orderEntry", "type" to "inheritedJdk")
            xml("orderEntry", "type" to "sourceFolder", "forTests" to "false")
            for (library in libraries) {
                xml("orderEntry", "type" to "library", "scope" to "PROVIDED", "name" to library.name, "level" to "project")
            }
        }
    }.render(addXmlDeclaration = true)
}

private fun updateFile(sourceFile: Path, regexp: String, replacement: String) {
    val updatedFileContent = sourceFile.readText().replace(
        Regex(regexp), replacement
    )

    sourceFile.writeText(updatedFileContent)
}
