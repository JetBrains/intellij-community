// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

    for ((root, isCommunity) in listOf(monorepoRoot to false, communityRoot to true)) {
        if (root == null) continue
        val parsedArgs = Args(defaultArgs + manualArgs)
        generateProjectModelFiles(root.resolve(".idea"), parsedArgs, isCommunity)
        patchProjectModelFiles(root.resolve(".idea"), parsedArgs, isCommunity)
    }
}

private fun generateProjectModelFiles(dotIdea: File, args: Args, isCommunity: Boolean) {
    val libraries = dotIdea.resolve("libraries")
    libraries.listFiles()!!.filter { it.startsWith("kotlinc_") }.forEach { it.delete() }
    generateKotlincLibraries(args.kotlincArtifactsMode, args.kotlincVersion, isCommunity).forEach {
        val libXmlName = it.name.jpsEntityNameToFilename() + ".xml"
        libraries.resolve(libXmlName).writeText(it.generateXml())
    }

    generateRunConfigurations(dotIdea, args.kotlincArtifactsMode, args.kotlincVersion)
}

private fun patchProjectModelFiles(dotIdea: File, args: Args, isCommunity: Boolean) {
    patchGitignore(dotIdea, args.kotlincArtifactsMode, isCommunity)
    patchKotlincLibs(args, isCommunity, dotIdea)
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

private fun patchKotlincLibs(args: Args, isCommunity: Boolean, dotIdea: File) {
    val kotlincMvnLibs = generateKotlincLibraries(KotlincArtifactsMode.MAVEN, args.kotlincVersion, isCommunity)

    val artifactIds = kotlincMvnLibs.flatMap { lib -> lib.classes.map { (it.jpsPath as JpsPath.MavenRepository).mavenId.artifactId } }
    val replacements = generateKotlincLibraries(args.kotlincArtifactsMode, args.kotlincVersion, isCommunity)
        .flatMap { lib -> lib.classes.map { it.jpsPath.generateXml() } }

    val stubVersion = "STUB_VERSION"
    val regexes = KotlincArtifactsMode.values().flatMap { generateKotlincLibraries(it, stubVersion, isCommunity) }
        .flatMap { it.classes }
        .map {
            it.jpsPath.generateXml().escapeForRegex().replace(stubVersion, """\d+.\d+.\d+(-SNAPSHOT|-M1|-M2|-RC|-release)?(-\d+)?""")
        }
        .distinct()
        .map { Regex(it) }
        .toList()

    for (artifactXmlFile in dotIdea.resolve("artifacts").listFiles()!!) {
        for (artifactId in artifactIds) {
            val allMatchingRegexes = regexes.filter { it.toString().contains("\\/$artifactId\\/") }
            check(allMatchingRegexes.size == KotlincArtifactsMode.values().size) {
                "There should be only ${KotlincArtifactsMode.values().size} matching regexes"
            }
            val replacement = replacements.single { it.contains("/$artifactId/") }
            val newText = allMatchingRegexes.fold(artifactXmlFile.readText()) { text, regex ->
                regex.replace(text, Regex.escapeReplacement(replacement))
            }
            artifactXmlFile.writeText(newText)
        }
    }
}

private fun Iterable<String>.toMapOfArgs(): Map<String, String> = associate { arg ->
    arg.split("=").also {
        check(it.size == 2) { "All arguments are expected to be key value pairs in 'key=value' format but got '$arg'" }
    }.let { it[0] to it[1] }
}
