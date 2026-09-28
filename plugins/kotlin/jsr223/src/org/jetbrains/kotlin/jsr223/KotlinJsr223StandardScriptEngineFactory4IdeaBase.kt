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
 * The IDE's JSR-223 engine factory: produces a [KotlinJsr223DaemonScriptEngineImpl], i.e. an engine
 * that compiles every snippet **out-of-process**, in a Kotlin compile daemon spawned from the
 * `kotlinc` distribution [kotlincDirProvider] points at (see [Jsr223KotlincProvider]), so the IDE
 * process itself never hosts a Kotlin compiler for JSR-223 evaluation.
 *
 * @param kotlincDirProvider the `kotlinc` home directory (the one containing `lib/` and `build.txt`)
 *   the compile daemon is spawned/identified with.
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

        // The classpath the daemon is spawned/identified with: the compiler itself plus the plain,
        // unshaded scripting compiler plugin jars, so the daemon discovers the scripting plugin
        // through its own META-INF/services entries -- see DaemonReplCompiler's KDoc.
        val compilerClasspath = kotlinPaths.classPath(KotlinPaths.ClassPaths.CompilerWithScripting)

        // Every snippet's own compile classpath: the stdlib (which the daemon compile does not add
        // implicitly) and the script runtime (which supplies ScriptTemplateWithBindings, one of the
        // implicit receivers the bindings-exposing synthetic snippet compiles against).
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
