// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
/*
 * Copyrig()ht 2010-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jetbrains.kotlin.idea.scratch

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.application.*
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdkVersion
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.findDocument
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.util.PathUtil
import com.intellij.util.io.awaitExit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifacts
import org.jetbrains.kotlin.idea.base.psi.getLineNumber
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinPluginLayout
import org.jetbrains.kotlin.idea.core.script.KotlinScratchScript
import org.jetbrains.kotlin.idea.core.script.scriptClassPath
import org.jetbrains.kotlin.idea.scratch.actions.RunScratchAction.ExplainInfo
import org.jetbrains.kotlin.idea.scratch.output.ScratchOutput
import org.jetbrains.kotlin.idea.scratch.output.ScratchOutputHandler
import org.jetbrains.kotlin.idea.util.JavaParametersBuilder
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.idea.util.projectStructure.version
import java.net.URLClassLoader
import java.nio.file.Path
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString

abstract class ScratchExecutor(protected val file: ScratchFile) {
    abstract fun execute()
    abstract fun stop()

    protected val handler: CompositeOutputHandler = CompositeOutputHandler()

    fun addOutputHandler(outputHandler: ScratchOutputHandler) {
        handler.add(outputHandler)
    }

    fun errorOccurs(message: String, e: Throwable? = null, isFatal: Boolean = false) {
        handler.error(file, message)

        if (isFatal) {
            handler.onFinish(file)
        }

        if (e != null && (e !is ControlFlowException)) LOG.error(e)
    }

    class CompositeOutputHandler : ScratchOutputHandler {
        private val handlers = mutableSetOf<ScratchOutputHandler>()

        fun add(handler: ScratchOutputHandler) {
            handlers.add(handler)
        }

        fun remove(handler: ScratchOutputHandler) {
            handlers.remove(handler)
        }

        override fun onStart(file: ScratchFile) {
            handlers.forEach { it.onStart(file) }
        }

        override fun handle(file: ScratchFile, infos: List<ExplainInfo>, scope: CoroutineScope) {
            handlers.forEach { it.handle(file, infos, scope) }
        }

        override fun handle(file: ScratchFile, expression: ScratchExpression, output: ScratchOutput) {
            handlers.forEach { it.handle(file, expression, output) }
        }

        override fun error(file: ScratchFile, message: String) {
            handlers.forEach { it.error(file, message) }
        }

        override fun onFinish(file: ScratchFile) {
            handlers.forEach { it.onFinish(file) }
        }

        override fun clear(file: ScratchFile) {
            handlers.forEach { it.clear(file) }
        }
    }
}

class K2ScratchExecutor(val scratchFile: ScratchFile, val project: Project, val scope: CoroutineScope) : ScratchExecutor(scratchFile) {
    override fun execute() {
        handler.onStart(file)

        val scriptFile = scratchFile.file

        scope.launch {
            val document = readAction { scriptFile.findDocument() }
            if (document != null) {
                writeAction {
                    PsiDocumentManager.getInstance(project).commitDocument(document)
                    FileDocumentManager.getInstance().saveDocument(document)
                }
            }
        }

        val compilationResult = runBlockingCancellable {
            compileScript(scriptFile)
        }

        val infos = if (compilationResult != 0) {
            val failed = ExplainInfo("", 0 to 0, "Compilation failed with code $compilationResult", 0)
            listOf(failed)
        } else {
            runCompiledScript(scriptFile).map { (key, value) ->
                val leftBracketIndex = key.indexOf("(")
                val rightBracketIndex = key.indexOf(")")
                val commaIndex = key.indexOf(",")

                val offsets =
                    key.substring(leftBracketIndex + 1, commaIndex).toInt() to key.substring(commaIndex + 2, rightBracketIndex).toInt()

                ExplainInfo(
                    key.substring(0, leftBracketIndex), offsets, value, scratchFile.getPsiFile()?.getLineNumber(offsets.second)
                )
            }
        }

        handler.handle(scratchFile, infos, scope)
        handler.onFinish(file)
    }

    private suspend fun compileScript(scriptFile: VirtualFile): Int {
        val compileScriptCommand = getJavaCommandLine(scriptFile)
        return compileScriptCommand.createProcess().awaitExit()
    }

    private fun getJavaCommandLine(scriptFile: VirtualFile): GeneralCommandLine {
        val javaParameters = JavaParametersBuilder(project).withSdkFrom(ModuleUtilCore.findModuleForFile(scriptFile, project), true)
            .withMainClassName("org.jetbrains.kotlin.cli.jvm.K2JVMCompiler").build()

        javaParameters.charset = null
        with(javaParameters.vmParametersList) {
            if (isUnitTestMode() && javaParameters.jdk?.version?.isAtLeast(JavaSdkVersion.JDK_1_9) == true) { // TODO: Have to get rid of illegal access to java.util.ResourceBundle.setParent(java.util.ResourceBundle):
                //  WARNING: Illegal reflective access by com.intellij.util.ReflectionUtil (file:...kotlin-ide/intellij/out/kotlinc-dist/kotlinc/lib/kotlin-compiler.jar) to method java.util.ResourceBundle.setParent(java.util.ResourceBundle)
                //  WARNING: Please consider reporting this to the maintainers of com.intellij.util.ReflectionUtil
                //  WARNING: Use --illegal-access=warn to enable warnings of further illegal reflective access operations
                //  WARNING: All illegal access operations will be denied in a future release
                add("--add-opens")
                add("java.base/java.util=ALL-UNNAMED")
            }
        }

        val ideScriptingClasses = PathUtil.getJarPathForClass(KotlinScratchScript::class.java)


        val classPath = buildSet {
            val classPath = listOf( // KotlinPaths.ClassPaths.CompilerWithScripting + jetbrains-annotations
                KotlinArtifacts.kotlinCompiler,
                KotlinArtifacts.kotlinStdlib,
                KotlinArtifacts.kotlinReflect,
                KotlinArtifacts.kotlinScriptRuntime,
                KotlinArtifacts.trove4j,
                KotlinArtifacts.kotlinDaemon,
                KotlinArtifacts.kotlinScriptingCompiler,
                KotlinArtifacts.kotlinScriptingCompilerImpl,
                KotlinArtifacts.kotlinScriptingCommon,
                KotlinArtifacts.kotlinScriptingJvm,
                KotlinArtifacts.jetbrainsAnnotations
            ).map { it.toPath().absolutePathString() }

            addAll(classPath)
            add(ideScriptingClasses)
            //addAll((scriptClassPath).map { it.toPath().absolutePathString() })
        }.toList()

        javaParameters.classPath.addAll(classPath)
        javaParameters.programParametersList.addAll(
            "-kotlin-home", KotlinPluginLayout.kotlinc.toPath().absolutePathString(),
            scriptFile.path,
            "-d", getPathToScriptJar(scriptFile).absolutePathString(),
            "-Xplugin=${KotlinArtifacts.powerAssertPlugin.toPath().absolutePathString()}",
            "-script-templates", KotlinScratchScript::class.java.name,
            "-Xuse-fir-lt=false",
            "-Xallow-any-scripts-in-source-roots",
            "-P", "plugin:kotlin.scripting:disable-script-definitions-autoloading=true",
            "-P", "plugin:kotlin.scripting:disable-standard-script=true",
            "-P", "plugin:kotlin.scripting:enable-script-explanation=true"
        )

        return javaParameters.toCommandLine()
    }

    private fun getKotlincCommandLine(scriptFile: VirtualFile): GeneralCommandLine {
        val ideScriptingClasses = PathUtil.getJarPathForClass(KotlinScratchScript::class.java)
        val classPath = listOf(ideScriptingClasses) + scriptClassPath.map { it.toPath() }

        val compileScriptCommand = GeneralCommandLine().withExePath(KotlinArtifacts.kotlinc.absolutePath)
            //.withParameters("-J-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:5006").withParameters(scriptFile.path)
            .withParameters("-d", getPathToScriptJar(scriptFile).absolutePathString())
            .withParameters("-Xplugin=${KotlinArtifacts.powerAssertPlugin.absolutePath}")
            .withParameters("-cp", classPath.joinToString(separator = ":"))
            .withParameters("-script-templates", KotlinScratchScript::class.java.name).withParameters("-Xuse-fir-lt=false")
            .withParameters("-Xallow-any-scripts-in-source-roots")
            .withParameters("-P", "plugin:kotlin.scripting:disable-script-definitions-autoloading=true")
            .withParameters("-P", "plugin:kotlin.scripting:disable-standard-script=true")
            .withParameters("-P", "plugin:kotlin.scripting:enable-script-explanation=true")
        return compileScriptCommand
    }

    private fun runCompiledScript(scriptFile: VirtualFile): MutableMap<String, Any> {
        val pathToJar = getPathToScriptJar(scriptFile)
        val kotlinPluginJar = Path.of(PathUtil.getJarPathForClass(KotlinScratchScript::class.java))

        val urls = listOf(
            kotlinPluginJar,
            pathToJar,
        ).map { it.toUri().toURL() }.toTypedArray()

        val classFileName = scriptFile.nameWithoutExtension.capitalize()
        val classLoader = URLClassLoader.newInstance(urls)

        val results: MutableMap<String, Any> = mutableMapOf()
        try {
            val loadedClass = classLoader.loadClass(classFileName)
            loadedClass.constructors.single().newInstance(results)
        } catch (e: Exception) {
            results.put("(0, 0)", e)
        }

        return results
    }

    fun getPathToScriptJar(scriptFile: VirtualFile): Path = Path(PathManager.getTempPath()).resolve(scriptFile.name.replace(".kts", ".jar"))

    override fun stop() {
        handler.onFinish(file)
    }
}

abstract class SequentialScratchExecutor(file: ScratchFile) : ScratchExecutor(file) {
    abstract fun executeStatement(expression: ScratchExpression)

    protected abstract fun startExecution()
    protected abstract fun stopExecution(callback: (() -> Unit)? = null)

    protected abstract fun needProcessToStart(): Boolean

    fun start() {
        EditorFactory.getInstance().eventMulticaster.addDocumentListener(listener, file.project.messageBus.connect())

        startExecution()
    }

    override fun stop() {
        EditorFactory.getInstance().eventMulticaster.removeDocumentListener(listener)

        stopExecution()
    }

    fun executeNew() {
        val expressions = file.getExpressions()
        if (wasExpressionExecuted(expressions.size)) return

        handler.onStart(file)

        for ((index, expression) in expressions.withIndex()) {
            if (wasExpressionExecuted(index)) continue

            executeStatement(expression)
            lastExecuted = index
        }
    }

    override fun execute() {
        if (needToRestartProcess()) {
            resetLastExecutedIndex()
            handler.clear(file)

            handler.onStart(file)
            stopExecution {
                ApplicationManager.getApplication().invokeLater {
                    executeNew()
                }
            }
        } else {
            executeNew()
        }
    }

    fun getFirstNewExpression(): ScratchExpression? {
        val expressions = runReadAction { file.getExpressions() }
        val firstNewExpressionIndex = lastExecuted + 1
        if (firstNewExpressionIndex in expressions.indices) {
            return expressions[firstNewExpressionIndex]
        }
        return null
    }

    private val listener = object : DocumentListener {
        override fun documentChanged(event: DocumentEvent) {
            if (event.newFragment.isBlank() && event.oldFragment.isBlank()) return
            if (!needToRestartProcess()) return

            val document = event.document
            val virtualFile = FileDocumentManager.getInstance().getFile(document)?.takeIf { it.isInLocalFileSystem } ?: return
            if (!virtualFile.isValid) {
                return
            }

            if (PsiManager.getInstance(file.project).findFile(virtualFile) != file.getPsiFile()) return

            val changedLine = document.getLineNumber(event.offset)
            val changedExpression = file.getExpressionAtLine(changedLine) ?: return
            val changedExpressionIndex = file.getExpressions().indexOf(changedExpression)
            if (wasExpressionExecuted(changedExpressionIndex)) {
                resetLastExecutedIndex()
                handler.clear(file)

                stopExecution()
            }
        }
    }

    private var lastExecuted = -1

    private fun needToRestartProcess(): Boolean {
        return lastExecuted > -1
    }

    private fun resetLastExecutedIndex() {
        lastExecuted = -1
    }

    private fun wasExpressionExecuted(index: Int): Boolean {
        return index <= lastExecuted
    }

    @TestOnly
    fun stopAndWait() {
        val lock = Semaphore(1)
        lock.acquire()
        stopExecution {
            lock.release()
        } // blocking UI thread!?
        check(lock.tryAcquire(2, TimeUnit.SECONDS)) {
            "Couldn't stop REPL process in 2 seconds"
        }
    }
}