// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.tools.model.updater

import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.text.replace

class BazelKotlinDependencyFacade(val communityRoot: Path) {
    companion object {
        private val FILE = KotlinTestsDependenciesUtil.communityRoot
            .resolve("plugins/kotlin/kotlin_test_dependencies.bzl")

        private val KOTLIN_COMPILER_CLI_VERSION_CONST = DependencyConstant("kotlinCompilerCliVersion")
        private val KOTLINC_KOTLIN_JPS_PLUGIN_TESTS_VERSION_CONST = DependencyConstant("kotlincKotlinJpsPluginTestsVersion")

        private val NAME_REGEX = Regex("""name\s*=\s*["']([^"']+)["']""")
        private val SHA256_REGEX = Regex("""sha256\s*=\s*["']([^"']+)["']""")
        private val SIMPLE_URL_REGEX = Regex("""url\s*=\s*["'](.+)["']""")
        private val PARAMETRIZED_URL_REGEX = Regex("""url\s*=\s*["'](.+)["']\.format\((.+)\),""")

        fun write(newContent: String) {
            FILE.writeText(newContent)
        }

        /**
         * Update the Bazel script, replacing values assigned to constants with the provided replacements.
         *
         * @param newKotlinCompilerCliVersion Version of the main 'kotlinc.' artifacts.
         * @param newKotlinJpsPluginTestsVersion Version of the Kotlin JPS plugin artifacts.
         */
        fun update(
            newKotlinCompilerCliVersion: String,
            newKotlinJpsPluginTestsVersion: String,
        ) {
            var newContent = FILE.readText()

            fun replace(const: DependencyConstant, newValue: String?) {
                if (newValue == null) return
                newContent = newContent.replace(const.regex, const.name + " = \"" + newValue + "\"")
            }

            replace(KOTLIN_COMPILER_CLI_VERSION_CONST, newKotlinCompilerCliVersion)
            replace(KOTLINC_KOTLIN_JPS_PLUGIN_TESTS_VERSION_CONST, newKotlinJpsPluginTestsVersion)

            FILE.writeText(newContent)
        }
    }

    private class DependencyConstant private constructor(val name: String, val regex: Regex) {
        constructor(name: String) : this(name, Regex("""$name\s*=\s*"(\S+)""""))
    }

    init {
        check(FILE.isRegularFile())
    }

    /**
     * The entire, unchanged content of the [FILE].
     */
    val content: String = FILE.readText()

    /**
     * Substitution constants specified in the [FILE] in the format `mapOf(constantName to value)`.
     */
    private val constants: Map<String, String> by lazy {
        fun get(constant: DependencyConstant): Pair<String, String> {
            val match = constant.regex.find(content) ?: error("Cannot find constant '${constant.name}' in content:\n$content")
            return constant.name to match.groupValues[1]
        }

        mapOf(
            get(KOTLIN_COMPILER_CLI_VERSION_CONST),
            get(KOTLINC_KOTLIN_JPS_PLUGIN_TESTS_VERSION_CONST),
        )
    }

    /**
     * All 'download_file' dependencies declared in the [FILE].
     */
    val dependencies: List<Dependency> by lazy {
        fun parseFileBlocks(): List<String> {
            val blocks = mutableListOf<String>()
            val regex = Regex("""download_file\s*\(""")

            for (match in regex.findAll(content)) {
                val startPos = match.range.last + 1
                var depth = 1
                var pos = startPos

                while (pos < content.length && depth > 0) {
                    when (content[pos]) {
                        '(' -> depth++
                        ')' -> depth--
                    }
                    pos++
                }

                if (depth == 0) {
                    blocks.add(content.substring(startPos, pos - 1))
                }
            }

            return blocks.filter { it.contains("=") }
        }

        buildList {
            for (block in parseFileBlocks()) {
                val name = NAME_REGEX.find(block)?.groupValues?.get(1)
                    ?: error("Cannot find the 'name' attribute in content:\n$content")

                val sha256 = SHA256_REGEX.find(block)?.groupValues?.get(1)
                    ?: error("Cannot find the 'sha256' attribute in content:\n$content")

                val parametrizedMatchResult = PARAMETRIZED_URL_REGEX.find(block)

                val dependency = if (parametrizedMatchResult != null) {
                    val rawUrl = parametrizedMatchResult.groupValues[1]
                    val versionName = parametrizedMatchResult.groupValues[2]

                    val version = constants[versionName] ?: error("Cannot find version $versionName in $constants")

                    val url = rawUrl
                        .replace("{0}", version)

                    Dependency(name, url, sha256, version)
                } else {
                    val simpleMatchResult = SIMPLE_URL_REGEX.find(block)
                        ?: error("cannot find url in '$block'")
                    val url = simpleMatchResult.groupValues[1]
                    Dependency(name, url, sha256, substitutedVersion = null)
                }

                add(dependency)
            }
        }
    }

    /**
     * A 'download_file' entry.
     *
     * @property name The artifact name.
     * @property url The artifact URL with all variables substituted.
     * @property sha256 SHA-256 checksum of the artifact.
     * @property substitutedVersion Artifact version is specified as a 'format' argument.
     */
    class Dependency(
        val name: String,
        val url: String,
        val sha256: String,
        val substitutedVersion: String? = null,
    )
}
