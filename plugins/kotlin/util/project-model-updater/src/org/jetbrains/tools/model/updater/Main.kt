// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.tools.model.updater

import org.jetbrains.tools.model.updater.GeneratorPreferences.ArtifactMode
import org.jetbrains.tools.model.updater.impl.*
import java.io.File
import java.util.*

class GeneratorPreferences(properties: Properties) : Preferences(properties) {
    val jpsPluginVersion: String by Preference()
    val jpsPluginArtifactsMode: ArtifactMode by Preference(ArtifactMode::valueOf)

    val kotlincVersion: String by Preference()
    val kotlincArtifactsMode: ArtifactMode by Preference(ArtifactMode::valueOf)

    enum class ArtifactMode {
        MAVEN, BOOTSTRAP
    }

    companion object {
        fun parse(args: Array<String>): GeneratorPreferences {
            val properties = Properties()

            val configurationFile = object {}::class.java.getResource("/model.properties")
            configurationFile?.openStream()?.use { stream -> properties.load(stream) }

            // Preferences passed as command line arguments override those from a configuration file
            for (arg in args.flatMap { it.split(" ") }) {
                val parts = arg.split('=')
                if (parts.size != 2) {
                    throw IllegalArgumentException("Invalid argument: $arg")
                }
                properties[parts[0]] = parts[1]
            }

            return GeneratorPreferences(properties)
        }
    }
}

fun main(args: Array<String>) {
    val preferences = GeneratorPreferences.parse(args)

    val communityRoot = generateSequence(File(".").canonicalFile) { it.parentFile }
        .first { it.resolve(".idea").isDirectory && !it.resolve("community").isDirectory }

    val monorepoRoot = communityRoot.resolve("..").takeIf { it.resolve(".idea").isDirectory }

    fun processRoot(root: File, isCommunity: Boolean) {
        val libraries = generateKotlincLibraries(preferences, isCommunity)
        regenerateProjectLibraries(root.resolve(".idea"), libraries)
    }

    if (monorepoRoot != null) {
        processRoot(monorepoRoot, isCommunity = false)
    }

    processRoot(communityRoot, isCommunity = true)
    patchGitignore(communityRoot, preferences.kotlincArtifactsMode)
}

private fun regenerateProjectLibraries(dotIdea: File, libraries: List<JpsLibrary>) {
    val librariesDir = dotIdea.resolve("libraries")
    librariesDir.listFiles { file -> file.startsWith("kotlinc_") }!!.forEach { it.delete() }

    for (library in libraries) {
        val libraryFileName = library.name.replace("\\W".toRegex(), "_") + ".xml"
        librariesDir.resolve(libraryFileName).writeText(library.render())
    }
}

private fun patchGitignore(dotIdea: File, kotlincArtifactsMode: ArtifactMode) {
    val gitignoreFile = dotIdea.resolve("..").resolve(".gitignore")
    val ignoreRule = "**/build"
    val normalizedContent = gitignoreFile.readLines().filter { it != ignoreRule }.joinToString("\n")

    when (kotlincArtifactsMode) {
        ArtifactMode.MAVEN -> gitignoreFile.writeText(normalizedContent)
        ArtifactMode.BOOTSTRAP -> gitignoreFile.writeText("$normalizedContent\n$ignoreRule")
    }
}
