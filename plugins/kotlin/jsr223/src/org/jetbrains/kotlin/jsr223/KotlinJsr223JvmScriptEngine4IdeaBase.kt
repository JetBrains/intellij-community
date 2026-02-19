// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.jsr223

import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.common.repl.GenericReplCompilingEvaluator
import org.jetbrains.kotlin.cli.common.repl.IReplStageState
import org.jetbrains.kotlin.cli.common.repl.KotlinJsr223JvmScriptEngineBase
import org.jetbrains.kotlin.cli.common.repl.ReplCompilerWithoutCheck
import org.jetbrains.kotlin.cli.common.repl.ReplFullEvaluator
import org.jetbrains.kotlin.cli.common.repl.ScriptArgsWithTypes
import org.jetbrains.kotlin.daemon.client.DaemonReportMessage
import org.jetbrains.kotlin.daemon.client.DaemonReportingTargets
import org.jetbrains.kotlin.daemon.client.KotlinCompilerClient
import org.jetbrains.kotlin.daemon.client.KotlinRemoteReplCompilerClient
import org.jetbrains.kotlin.daemon.common.CompileService
import org.jetbrains.kotlin.daemon.common.CompilerId
import org.jetbrains.kotlin.daemon.common.DaemonJVMOptions
import org.jetbrains.kotlin.daemon.common.configureDaemonOptions
import org.jetbrains.kotlin.daemon.common.makeAutodeletingFlagFile
import org.jetbrains.kotlin.utils.KotlinPaths
import org.jetbrains.kotlin.utils.KotlinPathsFromHomeDir
import java.io.File
import java.util.concurrent.locks.ReentrantReadWriteLock
import javax.script.ScriptContext
import javax.script.ScriptEngineFactory
import javax.script.ScriptException
import kotlin.io.path.exists
import kotlin.reflect.KClass

// TODO: need to manage resources here, i.e. call replCompiler.dispose when engine is collected

open class KotlinJsr223JvmScriptEngine4IdeaBase(
    factory: ScriptEngineFactory,
    templateClasspath: List<File>,
    templateClassName: String,
    private val kotlincDirProvider: () -> File,
    private val getScriptArgs: (ScriptContext, Array<out KClass<out Any>>?) -> ScriptArgsWithTypes?,
    private val scriptArgsTypes: Array<out KClass<out Any>>?
) : KotlinJsr223JvmScriptEngineBase(factory) {

    private val daemon by lazy {
        val libPath = KotlinPathsFromHomeDir(kotlincDirProvider.invoke())
        val classPath = libPath.classPath(KotlinPaths.ClassPaths.CompilerWithScripting)
        assert(classPath.all { it.toPath().exists() })
        val compilerId = CompilerId.makeCompilerId(classPath)
        val daemonOptions = configureDaemonOptions()
        val daemonJVMOptions = DaemonJVMOptions()

        val daemonReportMessages = arrayListOf<DaemonReportMessage>()

        KotlinCompilerClient.connectToCompileService(
            compilerId, daemonJVMOptions, daemonOptions,
            DaemonReportingTargets(null, daemonReportMessages),
            autostart = true, checkId = true
        ) ?: throw ScriptException(
            "Unable to connect to repl server:" + daemonReportMessages.joinToString("\n  ", prefix = "\n  ") {
                "${it.category.name} ${it.message}"
            }
        )
    }

    private val messageCollector = MyMessageCollector()

    override val replCompiler: ReplCompilerWithoutCheck by lazy {
        KotlinRemoteReplCompilerClient(
            daemon,
            makeAutodeletingFlagFile("idea-jsr223-repl-session"),
            CompileService.TargetPlatform.JVM,
            emptyArray(),
            messageCollector,
            templateClasspath,
            templateClassName
        )
    }

    override fun overrideScriptArgs(context: ScriptContext): ScriptArgsWithTypes? =
        getScriptArgs(getContext(), scriptArgsTypes)

    private val localEvaluator: ReplFullEvaluator by lazy {
        GenericReplCompilingEvaluator(replCompiler, templateClasspath, Thread.currentThread().contextClassLoader)
    }

    override val replEvaluator: ReplFullEvaluator get() = localEvaluator

    override fun createState(lock: ReentrantReadWriteLock): IReplStageState<*> = replEvaluator.createState(lock)

    private class MyMessageCollector : MessageCollector {
        private var hasErrors: Boolean = false

        override fun report(severity: CompilerMessageSeverity, message: String, location: CompilerMessageSourceLocation?) {
            System.err.println(message) // TODO: proper location printing
            if (!hasErrors) {
                hasErrors = severity == CompilerMessageSeverity.EXCEPTION || severity == CompilerMessageSeverity.ERROR
            }
        }

        override fun clear() {}

        override fun hasErrors(): Boolean = hasErrors
    }
}
