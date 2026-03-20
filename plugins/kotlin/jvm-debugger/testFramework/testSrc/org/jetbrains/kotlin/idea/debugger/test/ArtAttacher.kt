// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.test

import com.android.tools.idea.debug.AndroidFieldVisibilityProvider
import com.intellij.core.CoreApplicationEnvironment
import com.intellij.debugger.engine.FieldVisibilityProvider
import com.intellij.debugger.engine.RemoteStateState
import com.intellij.debugger.impl.DebuggerSession
import com.intellij.debugger.impl.RemoteConnectionBuilder
import com.intellij.debugger.settings.DebuggerSettings
import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.configurations.RemoteConnection
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.observable.util.whenDisposed
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.ui.classFilter.ClassFilter
import com.intellij.util.io.Compressor
import com.intellij.util.io.delete
import org.jetbrains.kotlin.android.debugger.AndroidDexerImpl
import org.jetbrains.kotlin.idea.debugger.core.DexBytecodeInspector
import org.jetbrains.kotlin.idea.debugger.evaluate.classLoading.AndroidDexer
import java.lang.ProcessBuilder.Redirect.PIPE
import java.lang.reflect.Method
import java.net.URI
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.security.MessageDigest
import kotlin.LazyThreadSafetyMode.NONE
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.pathString

private const val STUDIO_ROOT_ENV = "INTELLIJ_DEBUGGER_TESTS_STUDIO_ROOT"
private const val STUDIO_ROOT_PROPERTY = "intellij.debugger.tests.studio.root"
private const val TIMEOUT_MILLIS_ENV = "INTELLIJ_DEBUGGER_TESTS_TIMEOUT_MILLIS"
private const val TIMEOUT_MILLIS_PROPERTY = "intellij.debugger.tests.timeout.millis"
private const val DEX_CACHE_ENV = "INTELLIJ_DEBUGGER_TESTS_DEX_CACHE"
private const val DEX_CACHE_PROPERTY = "intellij.debugger.tests.dex.cache"

private const val DEX_COMPILER = "prebuilts/r8/r8.jar"
private const val ART_ROOT = "prebuilts/tools/linux-x86_64/art"
private const val LIB_ART = "framework/core-libart-hostdex.jar"
private const val OJ = "framework/core-oj-hostdex.jar"
private const val ICU4J = "framework/core-icu4j-hostdex.jar"
private const val ART = "bin/art"
private const val JVMTI = "lib64/libopenjdkjvmti.so"
private const val JDWP = "lib64/libjdwp.so"

private val DEX_LOCK = Any()
private val DEX_CACHE by lazy { getDexCache() }
private val ROOT by lazy(NONE) { getStudioRoot() }
private val D8_COMPILER by lazy(NONE) { loadD8Compiler() }

/** Private in [FieldVisibilityProvider] */
private val FIELD_VISIBILITY_PROVIDER_EP =
    ExtensionPointName.create<FieldVisibilityProvider>("com.intellij.debugger.fieldVisibilityProvider")

/** Attaches to an ART VM */
@Suppress("unused")
internal class ArtAttacher : VmAttacher {
    private lateinit var steppingFilters: Array<ClassFilter>
    private val disposable = Disposer.newDisposable("ArtAttacher")

    override fun setUp() {
        steppingFilters = DebuggerSettings.getInstance().steppingFilters
        DebuggerSettings.getInstance().steppingFilters +=
            arrayOf(
                ClassFilter("android.*"),
                ClassFilter("com.android.*"),
                ClassFilter("androidx.*"),
                ClassFilter("libcore.*"),
                ClassFilter("dalvik.*"),
            )
    }

    override fun tearDown() {
        DebuggerSettings.getInstance().steppingFilters = steppingFilters
        Disposer.dispose(disposable)
    }

    override fun attachVirtualMachine(
        testCase: KotlinDescriptorTestCase,
        javaParameters: JavaParameters,
        environment: ExecutionEnvironment,
    ): DebuggerSession {
        val project = testCase.project
        val application = ApplicationManager.getApplication()

        // Register extensions
        project.registerExtension(AndroidDexer.extensionPointName, AndroidDexerImpl(project), disposable)
        application.registerExtension(FIELD_VISIBILITY_PROVIDER_EP, AndroidFieldVisibilityProvider(), disposable)

        val remoteConnection = getRemoteConnection(testCase, javaParameters)
        val remoteState = RemoteStateState(project, remoteConnection)
        return testCase.attachVirtualMachine(remoteState, environment, remoteConnection, false)
    }

    private fun getRemoteConnection(testCase: KotlinDescriptorTestCase, javaParameters: JavaParameters): RemoteConnection {
        println("Running on ART VM with DEX Cache")
        val timeout = getTestTimeoutMillis()
        if (timeout != null) {
            testCase.setTimeout(timeout)
        }
        val mainClass = javaParameters.mainClass
        val dexFiles = buildDexFiles(javaParameters.classPath.pathList)
        if (DEX_CACHE == null) {
            @Suppress("UnstableApiUsage") testCase.testRootDisposable.whenDisposed { dexFiles.forEach { it.delete() } }
        }
        val command = buildCommandLine(dexFiles, mainClass)
        val art = ProcessBuilder().command(command).redirectOutput(PIPE).start()
        testCase.project.putUserData(DexBytecodeInspector.DEX_FILES_KEY, dexFiles)
        val port: String =
            art.inputStream.bufferedReader().use {
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

    /** Builds a DEX file from a list of dependencies */
    private fun buildDexFiles(deps: List<String>): List<Path> {
        return deps.mapNotNull {
            val path = Path.of(it)
            when (path.isDirectory()) {
                true -> buildDexFromDir(path)
                false -> buildDexFromJar(path)
            }
        }
    }

    private fun buildDexFromDir(dir: Path): Path? {
        if (dir.listDirectoryEntries().isEmpty()) {
            return null
        }
        val jarFile = Files.createTempFile(null, ".jar")
        try {
            Compressor.Jar(jarFile).use { jar -> jar.addDirectory("", dir) }
            return buildDexFromJar(jarFile)
        } finally {
            jarFile.delete()
        }
    }

    private fun buildDexFromJar(jar: Path): Path {
        val fileName = "${jar.generateHash()}-dex.jar"

        val cached = DEX_CACHE?.resolve(fileName)
        if (cached?.exists() == true) {
            return cached
        }
        return synchronized(DEX_LOCK) {
            if (cached?.exists() == true) {
                return@synchronized cached
            }
            val targetPath = cached ?: Files.createTempFile(null, fileName)
            val tempFile = targetPath.resolveSibling(targetPath.fileName.pathString.replace(".jar", "-tmp.jar"))
            D8_COMPILER.invoke(null, arrayOf("--output", tempFile.pathString, "--min-api", "30", jar.pathString))
            Files.move(tempFile, targetPath, ATOMIC_MOVE, REPLACE_EXISTING)
            targetPath
        }
    }

    /** Builds the command line to run the ART JVM */
    private fun buildCommandLine(dexFiles: List<Path>, mainClass: String): List<String> {
        val artDir = ROOT.resolve(ART_ROOT)
        val bootClasspath = listOf(artDir.resolve(LIB_ART), artDir.resolve(OJ), artDir.resolve(ICU4J)).joinToString(":") { it.pathString }

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
            dexFiles.joinToString(separator = ":") { it.pathString },
            mainClass,
        )
    }
}

private fun getConfig(property: String, env: String): String? {
    return System.getProperty(property) ?: System.getenv(env)
}

private fun getTestTimeoutMillis() = getConfig(TIMEOUT_MILLIS_PROPERTY, TIMEOUT_MILLIS_ENV)?.toIntOrNull()

private fun getStudioRoot(): Path {
    val path = getConfig(STUDIO_ROOT_PROPERTY, STUDIO_ROOT_ENV) ?: throw IllegalStateException("Studio Root was not provided")
    val root = Path.of(path)
    if (root.isDirectory()) {
        return root
    }
    throw IllegalStateException("'$path' is not a directory")
}

private fun getDexCache(): Path? {
    val path = getConfig(DEX_CACHE_PROPERTY, DEX_CACHE_ENV)?.toNioPathOrNull() ?: return null
    path.createDirectories()
    return path
}

private fun Path.generateHash(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val bytes = Files.readAllBytes(this)
    val hashBytes = digest.digest(bytes)
    return hashBytes.joinToString(separator = "") { String.format("%02x", it) }
}

private fun loadD8Compiler(): Method {
    val classLoader = URLClassLoader(arrayOf(URI("file://${ROOT.resolve(DEX_COMPILER).pathString}").toURL()))
    val d8 = classLoader.loadClass("com.android.tools.r8.D8")
    return d8.getDeclaredMethod("main", Array<String>::class.java)
}

@Suppress("SameParameterValue")
private inline fun <reified T : Any> ComponentManager.registerExtension(ep: ExtensionPointName<T>, extension: T, disposable: Disposable) {
    CoreApplicationEnvironment.registerExtensionPoint(extensionArea, ep, T::class.java)
    extensionArea.getExtensionPoint(ep).registerExtension(extension, disposable)
}
