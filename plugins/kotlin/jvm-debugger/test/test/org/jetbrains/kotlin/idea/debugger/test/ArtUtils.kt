// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.test

import com.intellij.util.io.Compressor
import com.intellij.util.io.delete
import java.nio.file.Files
import java.nio.file.Path
import kotlin.LazyThreadSafetyMode.NONE
import kotlin.io.path.isDirectory
import kotlin.io.path.pathString
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit.MILLISECONDS

private const val RUN_ON_ART_ENV = "INTELLIJ_DEBUGGER_TESTS_ART"
private const val RUN_ON_ART_PROPERTY = "intellij.debugger.tests.art"
private const val STUDIO_ROOT_ENV = "INTELLIJ_DEBUGGER_TESTS_STUDIO_ROOT"
private const val STUDIO_ROOT_PROPERTY = "intellij.debugger.tests.studio.root"
private const val TIMEOUT_MILLIS_ENV = "INTELLIJ_DEBUGGER_TESTS_TIMEOUT_MILLIS"
private const val TIMEOUT_MILLIS_PROPERTY = "intellij.debugger.tests.timeout.millis"

private const val DEX_COMPILER = "prebuilts/r8/r8.jar"
private const val ART_ROOT = "prebuilts/tools/linux-x86_64/art"
private const val LIB_ART = "framework/core-libart-hostdex.jar"
private const val OJ = "framework/core-oj-hostdex.jar"
private const val ICU4J = "framework/core-icu4j-hostdex.jar"
private const val ART = "bin/art"
private const val JVMTI = "lib64/libopenjdkjvmti.so"
private const val JDWP = "lib64/libjdwp.so"

/**
 * A collection of methods that support running tests on an Android ART VM.
 *
 * Notes:
 * * Only supported on Linux
 * * Requires an internal Google `studio-main` repo.
 */
internal object ArtUtils {
    private val root by lazy(NONE) { getStudioRoot() }

    /**
     * Returns true if tests should be run on ART
     *
     * Can be set by providing a JVM property or via the environment. JVM property overrides environment.
     */
    fun runTestOnArt(): Boolean {
        val property = System.getProperty(RUN_ON_ART_PROPERTY)
        if (property != null) {
            // Property overrides environment
            return property.toBoolean()
        }
        return System.getenv(RUN_ON_ART_ENV)?.toBoolean() ?: false
    }

    fun getTestTimeoutMillis(): Int {
        val property = System.getProperty(TIMEOUT_MILLIS_PROPERTY)
        if (property != null) {
            // Property overrides environment
            return property.toInt()
        }
        return System.getenv(TIMEOUT_MILLIS_ENV)?.toInt() ?: 30.seconds.toInt(MILLISECONDS)
    }

    /**
     * Builds the command line to run the ART JVM
     */
    fun buildCommandLine(dexFile: String, mainClass: String): List<String> {
        val artDir = root.resolve(ART_ROOT)
        val bootClasspath = listOf(
            artDir.resolve(LIB_ART),
            artDir.resolve(OJ),
            artDir.resolve(ICU4J),
        ).joinToString(":") { it.pathString }

        val art = artDir.resolve(ART).pathString
        val jvmti = artDir.resolve(JVMTI).pathString
        val jdwp = artDir.resolve(JDWP).pathString
        return listOf(
            art,
            "--64",
            "-Xbootclasspath:$bootClasspath",
            "-Xplugin:$jvmti",
            "-agentpath:$jdwp=transport=dt_socket,server=y,suspend=y",
            "-classpath",
            dexFile,
            mainClass,
        )
    }

    /**
     * Builds a DEX file from a list of dependencies
     */
    fun buildDexFile(deps: List<String>): Path {
        val dexCompiler = root.resolve(DEX_COMPILER)
        val tempFiles = mutableListOf<Path>()
        val jarFiles = deps.map { Path.of(it) }.map { path ->
            when {
                path.isDirectory() -> {
                    val jarFile = Files.createTempFile("", ".jar")
                    Compressor.Jar(jarFile).use { jar ->
                        jar.addDirectory("", path)
                    }
                    tempFiles.add(jarFile)
                    jarFile
                }

                else -> path
            }.pathString
        }
        try {
            val dexFile = Files.createTempFile("", "-dex.jar")
            Runtime.getRuntime().exec(
                "java -cp $dexCompiler com.android.tools.r8.D8 --output ${dexFile.pathString} --min-api 30 ${jarFiles.joinToString(" ") { it }}"
            ).waitFor()
            return dexFile
        } finally {
            tempFiles.forEach { it.delete() }
        }
    }

    private fun getStudioRoot(): Path {
        val property = System.getProperty(STUDIO_ROOT_PROPERTY)
        val env = System.getenv(STUDIO_ROOT_ENV)
        val path = property ?: env ?: throw IllegalStateException("Studio Root was not provided")
        val root = Path.of(path)
        if (root.isDirectory()) {
            return root
        }
        throw IllegalStateException("'$path' is not a directory")
    }
}
