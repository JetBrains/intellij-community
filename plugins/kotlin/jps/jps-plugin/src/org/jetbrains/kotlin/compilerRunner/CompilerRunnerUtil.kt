// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.compilerRunner

import org.jetbrains.kotlin.idea.artifacts.KotlinClassPath
import org.jetbrains.kotlin.preloading.ClassPreloadingUtils
import org.jetbrains.kotlin.preloading.Preloader
import java.io.File
import java.io.PrintStream
import java.lang.ref.SoftReference
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.name

object CompilerRunnerUtil {
    private var ourClassLoaderRef = SoftReference<ClassLoader>(null)

    internal val jdkToolsJar: File?
        get() {
            val javaHomePath = System.getProperty("java.home")
            if (javaHomePath == null || javaHomePath.isEmpty()) {
                return null
            }
            val javaHome = Path(javaHomePath)
            var toolsJar = javaHome.resolve("lib/tools.jar")
            if (toolsJar.exists()) {
                return toolsJar.toFile()
            }

            // We might be inside jre.
            if (javaHome.name == "jre") {
                toolsJar = javaHome.resolveSibling("lib/tools.jar")
                if (toolsJar.exists()) {
                    return toolsJar.toFile()
                }
            }

            return null
        }

    @Synchronized
    private fun getOrCreateClassLoader(
        environment: JpsCompilerEnvironment,
        paths: List<File>
    ): ClassLoader {
        var classLoader = ourClassLoaderRef.get()
        if (classLoader == null) {
            classLoader = ClassPreloadingUtils.preloadClasses(
                paths,
                Preloader.DEFAULT_CLASS_NUMBER_ESTIMATE,
                CompilerRunnerUtil::class.java.classLoader,
                environment.classesToLoadByParent
            )
            ourClassLoaderRef = SoftReference(classLoader)
        }
        return classLoader!!
    }

    fun invokeExecMethod(
        compilerClassName: String,
        arguments: Array<String>,
        environment: JpsCompilerEnvironment,
        out: PrintStream
    ): Any? = withCompilerClassloader(environment) { classLoader ->
        val compiler = Class.forName(compilerClassName, true, classLoader)
        val exec = compiler.getMethod(
            "execAndOutputXml",
            PrintStream::class.java,
            Class.forName("org.jetbrains.kotlin.config.Services", true, classLoader),
            Array<String>::class.java
        )
        exec.invoke(compiler.newInstance(), out, environment.services, arguments)
    }

    fun invokeClassesFqNames(
        environment: JpsCompilerEnvironment,
        files: Set<File>
    ): Set<String> = withCompilerClassloader(environment) { classLoader ->
        val klass = Class.forName("org.jetbrains.kotlin.incremental.parsing.ParseFileUtilsKt", true, classLoader)
        val method = klass.getMethod("classesFqNames", Set::class.java)
        @Suppress("UNCHECKED_CAST")
        method.invoke(klass, files) as? Set<String>
    } ?: emptySet()

    private fun <T> withCompilerClassloader(
        environment: JpsCompilerEnvironment,
        fn: (ClassLoader) -> T
    ): T? {
        val paths = KotlinClassPath.CompilerWithScripting.computeClassPath().let { classPath ->
            jdkToolsJar?.let { classPath + it } ?: classPath
        }

        val classLoader = getOrCreateClassLoader(environment, paths)
        return fn(classLoader)
    }
}
