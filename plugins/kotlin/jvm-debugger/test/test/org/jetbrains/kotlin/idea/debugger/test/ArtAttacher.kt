// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.test

import com.intellij.debugger.engine.RemoteStateState
import com.intellij.debugger.impl.DebuggerSession
import com.intellij.debugger.impl.RemoteConnectionBuilder
import com.intellij.debugger.settings.DebuggerSettings
import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.configurations.RemoteConnection
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.observable.util.whenDisposed
import com.intellij.ui.classFilter.ClassFilter
import com.intellij.util.io.Compressor
import com.intellij.util.io.delete
import java.lang.ProcessBuilder.Redirect.PIPE
import java.nio.file.Files
import java.nio.file.Path
import kotlin.LazyThreadSafetyMode.NONE
import kotlin.io.path.isDirectory
import kotlin.io.path.pathString
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit.MILLISECONDS

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

/** Attaches to an ART VM */
internal class ArtAttacher : VmAttacher {
    private val root by lazy(NONE) { getStudioRoot() }
    private lateinit var steppingFilters: Array<ClassFilter>

    override fun setUp() {
        steppingFilters = DebuggerSettings.getInstance().steppingFilters
        DebuggerSettings.getInstance().steppingFilters += arrayOf(
            ClassFilter("android.*"),
            ClassFilter("com.android.*"),
            ClassFilter("androidx.*"),
            ClassFilter("libcore.*"),
            ClassFilter("dalvik.*"),
        )
    }

    override fun tearDown() {
        DebuggerSettings.getInstance().steppingFilters = steppingFilters
    }

    override fun attachVirtualMachine(
        testCase: KotlinDescriptorTestCase,
        javaParameters: JavaParameters,
        environment: ExecutionEnvironment
    ): DebuggerSession {
        val remoteConnection = getRemoteConnection(testCase, javaParameters)
        val remoteState = RemoteStateState(testCase.project, remoteConnection)
        return testCase.attachVirtualMachine(remoteState, environment, remoteConnection, false)
    }

    private fun getRemoteConnection(testCase: KotlinDescriptorTestCase, javaParameters: JavaParameters): RemoteConnection {
        println("Running on ART VM")
        testCase.setTimeout(getTestTimeoutMillis())
        val mainClass = javaParameters.mainClass
        val dexFile = buildDexFile(javaParameters.classPath.pathList)
        val command = buildCommandLine(dexFile.pathString, mainClass)
        testCase.testRootDisposable.whenDisposed {
            dexFile.delete()
        }
        val art = ProcessBuilder()
            .command(command)
            .redirectOutput(PIPE)
            .start()

        val port: String = art.inputStream.bufferedReader().use {
            while (true) {
                val line = it.readLine() ?: break
                if (line.startsWith("Listening for transport")) {
                    val port = line.substringAfterLast(" ")
                    return@use port
                }
            }
            throw IllegalStateException("Failed to read listening port from ART")
        }

        return RemoteConnectionBuilder(false, DebuggerSettings.SOCKET_TRANSPORT, port)
            .checkValidity(true)
            .asyncAgent(true)
            .create(javaParameters)
    }

    /**
     * Builds a DEX file from a list of dependencies
     */
    private fun buildDexFile(deps: List<String>): Path {
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
            val command = arrayOf(
                "java",
                "-cp",
                dexCompiler.pathString,
                "com.android.tools.r8.D8",
                "--output",
                dexFile.pathString,
                "--min-api",
                "30"
            ) + jarFiles
            Runtime.getRuntime().exec(command).waitFor()
            return dexFile
        } finally {
            tempFiles.forEach { it.delete() }
        }
    }

    /**
     * Builds the command line to run the ART JVM
     */
    private fun buildCommandLine(dexFile: String, mainClass: String): List<String> {
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

}

private fun getTestTimeoutMillis(): Int {
    val property = System.getProperty(TIMEOUT_MILLIS_PROPERTY)
    if (property != null) {
        // Property overrides environment
        return property.toInt()
    }
    return System.getenv(TIMEOUT_MILLIS_ENV)?.toInt() ?: 30.seconds.toInt(MILLISECONDS)
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
