// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.test

import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.io.createDirectories
import org.jetbrains.kotlin.cli.common.CLICompiler
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.js.K2JSCompiler
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.config.JvmTarget
import org.jetbrains.kotlin.idea.base.plugin.artifacts.TestKotlinArtifacts
import org.jetbrains.kotlin.idea.test.KotlinCompilerStandalone.Platform.JavaScript
import org.jetbrains.kotlin.idea.test.KotlinCompilerStandalone.Platform.Jvm
import org.junit.Assert.assertEquals
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.lang.ref.SoftReference
import java.net.URLClassLoader
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempDirectory
import kotlin.io.path.div
import kotlin.reflect.KClass

class KotlinCompilerStandalone @JvmOverloads constructor(
    private val sources: List<File>,
    private val target: File = defaultTargetJar(),
    private val platform: Platform = Jvm(),
    private val options: List<String> = emptyList(),
    classpath: List<File> = emptyList(),
    includeKotlinStdlib: Boolean = true,
    private val compileKotlinSourcesBeforeJava: Boolean = true,
    private val jarWithSources: Boolean = false,
) {
    sealed class Platform {
        class JavaScript(val moduleName: String, val packageName: String) : Platform() {
            init {
                assert(moduleName.isNotEmpty())
                assert(packageName.isNotEmpty())
            }
        }

        class Jvm(val target: JvmTarget = JvmTarget.DEFAULT) : Platform()
    }

    companion object {
        @JvmStatic
        fun defaultTargetJar(): File {
            return File.createTempFile("kt-lib", ".jar")
                // TODO: [VD][to be fixed by Yan] as it affects test runs - jar is deleted while file reference is in use
                //.also { it.deleteOnExit() }
                .canonicalFile
        }

        fun copyToJar(sources: List<File>, prefix: String): File {
            with(File.createTempFile(prefix, ".jar").canonicalFile) {
                copyToJar(sources, this)
                return this
            }
        }

        fun copyToJar(sources: List<File>, target: File) {
            target.outputStream().buffered().use { os ->
                ZipOutputStream(os).use { zos ->
                    for (source in sources) {
                        for (file in source.walk()) {
                            if (file.isFile) {
                                val path = FileUtil.toSystemIndependentName(file.toRelativeString(source))
                                zos.putNextEntry(ZipEntry(path))
                                zos.write(file.readBytes())
                                zos.closeEntry()
                            }
                        }
                    }
                }
            }
        }

        fun copyToDirectory(sources: List<File>, target: File) {
            target.mkdirs()
            assert(target.isDirectory) { "Can't create target directory" }
            assert(target.listFiles().orEmpty().isEmpty()) { "Target directory is not empty" }
            sources.forEach { it.copyRecursively(target) }
        }
    }

    private val classpath: List<File>
    private val targetForJava: File?

    init {
        val completeClasspath = classpath.toMutableList()
        var targetForJava: File? = null

        if (includeKotlinStdlib) {
            when (platform) {
                is Jvm -> {
                    targetForJava = KotlinTestUtils.tmpDirForReusableFolder("java-lib")
                    completeClasspath += listOf(TestKotlinArtifacts.kotlinStdlib, TestKotlinArtifacts.jetbrainsAnnotations, targetForJava)
                }
                is JavaScript -> {
                    completeClasspath += TestKotlinArtifacts.kotlinStdlibJs
                }
            }
        }

        this.classpath = completeClasspath
        this.targetForJava = targetForJava
    }

    fun compile(): File {
        val ktFiles = mutableListOf<File>()
        val javaFiles = mutableListOf<File>()

        for (source in sources) {
            for (file in source.walk()) {
                if (file.isFile) {
                    when (file.extension) {
                        "java" -> javaFiles += file
                        "kt", "kts" -> ktFiles += file
                    }
                }
            }
        }

        assert(ktFiles.isNotEmpty() || javaFiles.isNotEmpty()) { "Sources not found" }
        assert(platform is Jvm || javaFiles.isEmpty()) { "Java source compilation is only available in JVM target" }

        val compilerTargets = mutableListOf<File>()

        fun compileJava() {
            if (javaFiles.isNotEmpty() && platform is Jvm) {
                compileJava(javaFiles, compilerTargets, targetForJava!!, useJava9 = platform.target >= JvmTarget.JVM_9)
                compilerTargets += targetForJava
            }
        }

        fun compileKotlin() {
            if (ktFiles.isNotEmpty()) {
                val targetForKotlin = KotlinTestUtils.tmpDirForReusableFolder("compile-kt")
                if (jarWithSources) {
                    // simple implementation
                    val src = File(targetForKotlin, "src")
                    for (file in ktFiles) {
                        file.copyTo(File(src, file.name))
                    }
                }

                when (platform) {
                    is Jvm -> compileKotlin(ktFiles, javaFiles.isNotEmpty(), targetForKotlin)
                    is JavaScript -> {
                        val targetJsDir = File(targetForKotlin, platform.moduleName)
                        val targetJsFile = File(targetJsDir, platform.packageName.substringAfterLast('.') + ".js")
                        compileKotlin(ktFiles, javaFiles.isNotEmpty(), targetJsFile)
                    }
                }
                compilerTargets += targetForKotlin
            }
        }

        if (compileKotlinSourcesBeforeJava) {
            compileKotlin()
            compileJava()
        } else {
            compileJava()
            compileKotlin()
        }

        val copyFun = if (target.extension.lowercase(Locale.getDefault()) == "jar") ::copyToJar else ::copyToDirectory
        copyFun(compilerTargets, target)

        compilerTargets.forEach { it.deleteRecursively() }

        return target
    }

    private fun compileKotlin(files: List<File>, hasJavaFiles: Boolean, target: File) {
        val args = mutableListOf<String>()

        args += files.map { it.absolutePath }
        if (classpath.isNotEmpty()) {
            when (platform) {
                is Jvm -> args += "-classpath"
                is JavaScript -> args += "-libraries"
            }

            args += classpath.joinToString(File.pathSeparator) { it.absolutePath }
        }

        args += "-no-stdlib"

        if (files.none { it.extension.lowercase(Locale.getDefault()) == "kts" }) {
            args += "-Xdisable-default-scripting-plugin"
        }

        args += options

        val kotlincFun = when (platform) {
            is Jvm -> {
                args += listOf("-d", target.absolutePath)
                if (hasJavaFiles) {
                    args += "-Xjava-source-roots=" + sources.joinToString(File.pathSeparator) { it.absolutePath }
                }
                KotlinCliCompilerFacade::runJvmCompiler
            }
            is JavaScript -> {
                args += listOf("-meta-info", "-output", target.absolutePath)
                KotlinCliCompilerFacade::runJsCompiler
            }
        }

        kotlincFun(args)
    }

    private fun compileJava(files: List<File>, existingCompilerTargets: List<File>, target: File, useJava9: Boolean) {
        val classpath = this.classpath + existingCompilerTargets

        val args = mutableListOf("-d", target.absolutePath)
        if (classpath.isNotEmpty()) {
            args += "-classpath"
            args += classpath.joinToString(File.pathSeparator) { it.absolutePath }
        }

        assert(KotlinTestUtils.compileJavaFiles(files, args)) { "Java files are not compiled successfully" }
    }

}

object KotlinCliCompilerFacade {
    private var classLoader = SoftReference<ClassLoader>(null)

    private val jvmCompilerClass: Class<*>
        @Synchronized get() = loadCompilerClass(K2JVMCompiler::class)

    private val jsCompilerClass: Class<*>
        @Synchronized get() = loadCompilerClass(K2JSCompiler::class)

    fun runJvmCompiler(args: List<String>) {
        runCompiler(jvmCompilerClass, args)
    }

    fun runJsCompiler(args: List<String>) {
        runCompiler(jsCompilerClass, args)
    }

    // Runs compiler in custom class loader to avoid effects caused by replacing Application with another one created in compiler.
    private fun runCompiler(compilerClass: Class<*>, args: List<String>) {
        val outStream = ByteArrayOutputStream()
        val compiler = compilerClass.getDeclaredConstructor().newInstance()
        val execMethod = compilerClass.getMethod("exec", PrintStream::class.java, Array<String>::class.java)
        val invocationResult = execMethod.invoke(compiler, PrintStream(outStream), args.toTypedArray()) as Enum<*>
        assertEquals(String(outStream.toByteArray(), StandardCharsets.UTF_8), ExitCode.OK.name, invocationResult.name)
    }

    @Synchronized
    private fun loadCompilerClass(compilerClass: KClass<out CLICompiler<*>>): Class<*> {
        val classLoader = classLoader.get() ?: createCompilerClassLoader().also { classLoader ->
            this.classLoader = SoftReference(classLoader)
        }
        return classLoader.loadClass(compilerClass.java.name)
    }

    @Synchronized
    private fun createCompilerClassLoader(): ClassLoader {
        val artifacts = listOf(
            TestKotlinArtifacts.kotlinStdlib,
            TestKotlinArtifacts.kotlinStdlibJdk7,
            TestKotlinArtifacts.kotlinStdlibJdk8,
            TestKotlinArtifacts.kotlinReflect,
            TestKotlinArtifacts.kotlinCompiler,
            TestKotlinArtifacts.kotlinScriptRuntime,
            TestKotlinArtifacts.kotlinScriptingCommon,
            TestKotlinArtifacts.kotlinScriptingCompiler,
            TestKotlinArtifacts.kotlinScriptingCompilerImpl,
            TestKotlinArtifacts.kotlinScriptingJvm,
            TestKotlinArtifacts.trove4j,
            TestKotlinArtifacts.kotlinDaemon,
            TestKotlinArtifacts.jetbrainsAnnotations,
        )

        // enable old backend support in compiler
        val tempDirWithOldBackedMarker = createTempDirectory()
        (tempDirWithOldBackedMarker / "META-INF" / "unsafe-allow-use-old-backend").createDirectories()

        val urls = (artifacts + tempDirWithOldBackedMarker.toFile()).map { it.toURI().toURL() }.toTypedArray()
        return URLClassLoader(urls, ClassLoader.getPlatformClassLoader())
    }
}