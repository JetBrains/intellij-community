// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:Suppress("unused") // used at runtime

package org.jetbrains.kotlin.test

import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.io.ZipUtil
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.cliArgument
import org.jetbrains.kotlin.codegen.forTestCompile.ForTestCompileRuntime
import org.jetbrains.kotlin.preloading.ClassPreloadingUtils
import org.jetbrains.kotlin.preloading.Preloader
import org.jetbrains.kotlin.test.KtAssert.assertTrue
import org.jetbrains.kotlin.test.util.KtTestUtil
import org.jetbrains.kotlin.utils.PathUtil
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.PrintStream
import java.lang.ref.SoftReference
import java.util.regex.Pattern
import java.util.zip.ZipOutputStream

object MockLibraryUtilExt {
    @JvmStatic
    fun compileJvmLibraryToJar(
        sourcesPath: String,
        jarName: String,
        addSources: Boolean = false,
        allowKotlinSources: Boolean = true,
        extraOptions: List<String> = emptyList(),
        extraClasspath: List<String> = emptyList(),
        useJava11: Boolean = false,
    ): File {
        return MockLibraryUtil.compileJvmLibraryToJar(
            sourcesPath,
            jarName,
            addSources,
            allowKotlinSources,
            extraOptions,
            emptyList(),
            extraClasspath,
            extraModulepath = listOf(),
            useJava11,
        )
    }

    @JvmStatic
    @JvmOverloads
    fun compileJvmLibraryToJar(
        sourcesPath: String,
        jarName: String,
        addSources: Boolean = false,
        allowKotlinSources: Boolean = true,
        extraOptions: List<String> = emptyList(),
        extraJavacOptions: List<String> = emptyList(),
        extraClasspath: List<String> = emptyList(),
        useJava11: Boolean = false,
    ): File {
        return MockLibraryUtil.compileJvmLibraryToJar(
            sourcesPath,
            jarName,
            addSources,
            allowKotlinSources,
            extraOptions,
            extraJavacOptions,
            extraClasspath,
            extraModulepath = listOf(),
            useJava11,
        )
    }
}

object MockLibraryUtil {
    private var compilerClassLoader = SoftReference<ClassLoader>(null)

    @JvmStatic
    @JvmOverloads
    @Deprecated("Use 'compileKotlinSources()' instead", level = DeprecationLevel.HIDDEN)
    fun compileKotlin(
        sourcesPath: String,
        outDir: File,
        extraOptions: List<String> = emptyList(),
        vararg extraClasspath: String,
    ) {
        compileKotlinSources(sourcesPath, outDir, extraOptions, *extraClasspath)
    }

    @JvmStatic
    @JvmOverloads
    fun compileJvmLibraryToJar(
        sourcesPath: String,
        jarName: String,
        addSources: Boolean = false,
        allowKotlinSources: Boolean = true,
        extraOptions: List<String> = emptyList(),
        extraJavacOptions: List<String> = emptyList(),
        extraClasspath: List<String> = emptyList(),
        useJava11: Boolean = false,
    ): File {
        return compileJvmLibraryToJar(
            sourcesPath,
            jarName,
            addSources,
            allowKotlinSources,
            extraOptions,
            extraJavacOptions,
            extraClasspath,
            extraModulepath = listOf(),
            useJava11
        )
    }

    fun compileJvmLibraryToJar(
        sourcesPath: String,
        jarName: String,
        addSources: Boolean = false,
        allowKotlinSources: Boolean = true,
        extraOptions: List<String> = emptyList(),
        extraJavacOptions: List<String> = emptyList(),
        extraClasspath: List<String> = emptyList(),
        extraModulepath: List<String> = emptyList(),
        useJava11: Boolean = false,
    ): File {
        return compileLibraryToJar(
            sourcesPath,
            KtTestUtil.tmpDirForReusableFolder("testLibrary-$jarName"),
            jarName,
            addSources,
            allowKotlinSources,
            extraOptions,
            extraJavacOptions,
            extraClasspath,
            extraModulepath,
            useJava11,
        )
    }

    fun compileLibraryToJar(
        sourcesPath: String,
        contentDir: File,
        jarName: String,
        addSources: Boolean = false,
        allowKotlinSources: Boolean = true,
        extraOptions: List<String> = emptyList(),
        extraJavacOptions: List<String> = emptyList(),
        extraClasspath: List<String> = emptyList(),
        extraModulepath: List<String> = emptyList(),
        useJava11: Boolean = false,
    ): File {
        assertTrue("Module path can be used only for compilation using javac 9 and higher", useJava11 || extraModulepath.isEmpty())

        val classesDir = File(contentDir, "classes")

        val srcFile = File(sourcesPath)
        val kotlinFiles = FileUtil.findFilesByMask(Pattern.compile(".*\\.kt"), srcFile)
        if (srcFile.isFile || kotlinFiles.isNotEmpty()) {
            assertTrue("Only java files are expected", allowKotlinSources)
            compileKotlinSources(sourcesPath, classesDir, extraOptions, *extraClasspath.toTypedArray())
        }

        val javaFiles = FileUtil.findFilesByMask(Pattern.compile(".*\\.java"), srcFile)
        if (javaFiles.isNotEmpty()) {
            val classpath = mutableListOf<String>()
            classpath += kotlinPathsForDistDirectoryForTestsOrNull?.stdlibPath?.path
                ?: ForTestCompileRuntime.runtimeJarForTests().path
            classpath += extraClasspath

            // Probably no kotlin files were present, so dir might not have been created after kotlin compiler
            if (classesDir.exists()) {
                classpath += classesDir.path
            } else {
                FileUtil.createDirectory(classesDir)
            }

            val options = buildList {
                add("-classpath")
                add(classpath.joinToString(File.pathSeparator))
                add("-d")
                add(classesDir.path)

                if (useJava11 && extraModulepath.isNotEmpty()) {
                    add("--module-path")
                    add(extraModulepath.joinToString(File.pathSeparator))
                }
                add("-encoding")
                add("utf8")
                addAll(extraJavacOptions)
            }

            val jdkHome = if (useJava11) KtTestUtil.getJdk11Home() else null
            compileJavaFiles(javaFiles, options, jdkHome).assertSuccessful()
        }

        return createJarFile(contentDir, classesDir, jarName, sourcesPath.takeIf { addSources })
    }

    fun createJarFile(contentDir: File, dirToAdd: File, jarName: String, sourcesPath: String? = null): File {
        val jarFile = File(contentDir, "$jarName.jar")

        ZipOutputStream(FileOutputStream(jarFile)).use { zip ->
            ZipUtil.addDirToZipRecursively(zip, jarFile, dirToAdd, "", null, null)
            if (sourcesPath != null) {
                ZipUtil.addDirToZipRecursively(zip, jarFile, File(sourcesPath), "src", null, null)
            }
        }

        return jarFile
    }

    fun runJvmCompiler(args: List<String>) {
        runCompiler(compiler2JVMClass, args)
    }

    // Runs compiler in custom class loader to avoid effects caused by replacing Application with another one created in compiler.
    private fun runCompiler(compilerClass: Class<*>, args: List<String>) {
        val outStream = ByteArrayOutputStream()

        @Suppress("DEPRECATION")
        val compiler = compilerClass.newInstance()
        val execMethod = compilerClass.getMethod("exec", PrintStream::class.java, Array<String>::class.java)
        val invocationResult = execMethod.invoke(compiler, PrintStream(outStream), args.toTypedArray()) as Enum<*>
        KtAssert.assertEquals(String(outStream.toByteArray()), ExitCode.OK.name, invocationResult.name)
    }

    fun compileKotlinSources(
        sourcesPath: String,
        outDir: File,
        extraOptions: List<String> = emptyList(),
        vararg extraClasspath: String
    ) {
        val classpath = mutableListOf<String>()
        if (File(sourcesPath).isDirectory) {
            classpath += sourcesPath
        }
        classpath += extraClasspath

        val args = mutableListOf(
            sourcesPath,
            K2JVMCompilerArguments::destination.cliArgument, outDir.absolutePath,
            K2JVMCompilerArguments::classpath.cliArgument, classpath.joinToString(File.pathSeparator)
        ) + extraOptions

        runJvmCompiler(args)
    }

    fun compileKotlinModule(buildFilePath: String) {
        runJvmCompiler(listOf(K2JVMCompilerArguments::noStdlib.cliArgument, K2JVMCompilerArguments::buildFile.cliArgument, buildFilePath))
    }

    private val compiler2JVMClass: Class<*>
        @Synchronized get() = loadCompilerClass("org.jetbrains.kotlin.cli.jvm.K2JVMCompiler")

    @Synchronized
    private fun loadCompilerClass(name: String): Class<*> {
        val classLoader = compilerClassLoader.get() ?: createCompilerClassLoader().also { classLoader ->
            compilerClassLoader = SoftReference<ClassLoader>(classLoader)
        }
        return classLoader.loadClass(name)
    }


    @Synchronized
    private fun createCompilerClassLoader(): ClassLoader {
        return ClassPreloadingUtils.preloadClasses(
            listOf(PathUtil.kotlinPathsForDistDirectoryForTests.compilerPath),
            Preloader.DEFAULT_CLASS_NUMBER_ESTIMATE, null, null
        )
    }
}