// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("IO_FILE_USAGE")

package org.jetbrains.kotlin.jsr223

import org.jetbrains.kotlin.config.KotlinCompilerVersion
import org.jetbrains.kotlin.jsr223.daemon.KotlinJsr223DaemonScriptEngineImpl
import org.jetbrains.kotlin.utils.KotlinPaths
import org.jetbrains.kotlin.utils.KotlinPathsFromHomeDir
import java.io.File
import javax.script.ScriptEngine
import javax.script.ScriptException
import kotlin.script.experimental.jvm.jsr223.base.KotlinJsr223ScriptEngineFactoryBase
import kotlin.script.experimental.jvm.util.scriptCompilationClasspathFromContextOrStdlib

/**
 * Compiles every snippet in a Kotlin compile daemon, not in the IDE process.
 *
 * @param kotlincDirProvider the kotlinc home that the daemon starts from.
 */
abstract class KotlinJsr223StandardScriptEngineFactory4IdeaBase(
    private val kotlincDirProvider: () -> File,
) : KotlinJsr223ScriptEngineFactoryBase() {

    override fun getEngineName(): String {
        return "Kotlin Script"
    }

    override fun getLanguageVersion(): String = KotlinCompilerVersion.VERSION
    override fun getEngineVersion(): String = KotlinCompilerVersion.VERSION

    override fun getScriptEngine(): ScriptEngine {
        val kotlinPaths = KotlinPathsFromHomeDir(kotlincDirProvider.invoke())

        // The unshaded scripting plugin jars, so the daemon finds the plugin through their META-INF/services.
        val compilerClasspath = kotlinPaths.classPath(KotlinPaths.ClassPaths.CompilerWithScripting)

        // The daemon does not add the stdlib. The script runtime has `ScriptTemplateWithBindings` for the binding snippet.
        val stdlibClasspath = kotlinPaths.classPath(KotlinPaths.Jar.StdLib, KotlinPaths.Jar.ScriptRuntime)

        val missingJars = (compilerClasspath + stdlibClasspath).filterNot { it.exists() }
        if (missingJars.isNotEmpty()) {
            throw ScriptException(
                "Incomplete Kotlin compiler distribution at ${kotlinPaths.homePath}, missing: " +
                        missingJars.joinToString { it.name }
            )
        }

        val snippetClasspath = scriptCompilationClasspathFromContextOrStdlib(wholeClasspath = true) + stdlibClasspath

        return KotlinJsr223DaemonScriptEngineImpl(
            this,
            compilerClasspath = compilerClasspath,
            additionalClasspath = snippetClasspath.map { it.toPath() },
            baseCompilationConfiguration = Jsr223DefaultScriptCompilationConfiguration,
            baseEvaluationConfiguration = Jsr223DefaultScriptEvaluationConfiguration,
        )
    }
}
