// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.tools.model.updater

import org.jetbrains.tools.model.updater.impl.*
import java.io.File
import java.util.*

fun main(args: Array<String>) {
    val manualArgs = args.flatMap { it.split(" ") }.toMapOfArgs()
    val defaultArgs = File(object {}::class.java.getResource("/model.properties")!!.toURI()).inputStream().use { stream ->
        Properties().apply { load(stream) }.map { (key, value) -> key as String to value as String }.toMap()
    }
    val communityRoot = generateSequence(File(".").canonicalFile) { it.parentFile }.first {
        it.resolve(".idea").isDirectory && !it.resolve("community").isDirectory
    }
    val monorepoRoot = communityRoot.resolve("..").takeIf { it.resolve(".idea").isDirectory }

    val parsedArgs = Args(defaultArgs + manualArgs)
    println("Args: $parsedArgs")
    for ((root, isCommunity) in listOf(monorepoRoot to false, communityRoot to true)) {
        if (root == null) continue
        generateProjectModelFiles(root.resolve(".idea"), parsedArgs, isCommunity)
        patchProjectModelFiles(root.resolve(".idea"), parsedArgs, isCommunity)
    }
    updateLatestGradlePluginVersion(communityRoot, parsedArgs.kotlinGradlePluginVersion)
}

private fun generateProjectModelFiles(dotIdea: File, args: Args, isCommunity: Boolean) {
    val libraries = dotIdea.resolve("libraries")
    libraries.listFiles()!!.filter { it.startsWith("kotlinc_") }.forEach { it.delete() }
    generateKotlincLibraries(
        kotlincArtifactsCoordinates = ArtifactCoordinates(args.kotlincVersion, args.kotlincArtifactsMode),
        jpsPluginCoordinates = ArtifactCoordinates(args.jpsPluginVersion, args.jpsPluginArtifactsMode),
        isCommunity = isCommunity
    ).forEach {
        val libXmlName = it.name.jpsEntityNameToFilename() + ".xml"
        libraries.resolve(libXmlName).writeText(it.generateXml())
    }
}

private fun patchProjectModelFiles(dotIdea: File, args: Args, isCommunity: Boolean) {
    patchGitignore(dotIdea, args.kotlincArtifactsMode, isCommunity)
}

private fun patchGitignore(dotIdea: File, kotlincArtifactsMode: KotlincArtifactsMode, isCommunity: Boolean) {
    if (!isCommunity) {
        return
    }
    val gitignore = dotIdea.resolve("..").resolve(".gitignore")
    val ignoreRule = "**/build"
    val normalizedContent = gitignore.readLines().filter { it != ignoreRule }.joinToString("\n")
    when (kotlincArtifactsMode) {
        KotlincArtifactsMode.MAVEN -> {
            gitignore.writeText(normalizedContent)
        }

        KotlincArtifactsMode.BOOTSTRAP -> {
            gitignore.writeText("$normalizedContent\n$ignoreRule")
        }
    }
}

/**
 * Updates the `KotlinGradlePluginVersions.kt` source file to contain the latest [kotlinGradlePluginVersion] in the source code.
 * The `KotlinGradlePluginVersions` source file can't directly read the `model.properties` file directly, since
 * the project model can be overwritten by the [main] args (see also [GeneratorPreferences.parse])
 */
private fun updateLatestGradlePluginVersion(communityRoot: File, kotlinGradlePluginVersion: String) {
    val sourceFile = communityRoot.resolve(
        "plugins/kotlin/gradle/gradle-java/tests/test/org/jetbrains/kotlin/idea/codeInsight/gradle/KotlinGradlePluginVersions.kt"
    )

    val updatedFileContent = sourceFile.readText().replace(
        Regex("""val latest = .*"""), "val latest = KotlinToolingVersion(\"$kotlinGradlePluginVersion\")"
    )

    sourceFile.writeText(updatedFileContent)
}

private fun Iterable<String>.toMapOfArgs(): Map<String, String> = associate { arg ->
    arg.split("=").also {
        check(it.size == 2) { "All arguments are expected to be key value pairs in 'key=value' format but got '$arg'" }
    }.let { it[0] to it[1] }
}
