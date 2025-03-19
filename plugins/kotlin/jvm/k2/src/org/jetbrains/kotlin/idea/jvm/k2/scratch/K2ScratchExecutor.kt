// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.jvm.k2.scratch

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.JavaSdkVersion
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.findDocument
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.psi.PsiDocumentManager
import com.intellij.util.PathUtil
import com.intellij.util.io.awaitExit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifactNames
import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifacts
import org.jetbrains.kotlin.idea.base.psi.getLineNumber
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinPluginLayout
import org.jetbrains.kotlin.idea.core.script.KotlinScratchScript
import org.jetbrains.kotlin.idea.jvm.shared.KotlinJvmBundle
import org.jetbrains.kotlin.idea.jvm.shared.scratch.ScratchExecutor
import org.jetbrains.kotlin.idea.jvm.shared.scratch.output.ExplainInfo
import org.jetbrains.kotlin.idea.util.JavaParametersBuilder
import org.jetbrains.kotlin.idea.util.application.isUnitTestMode
import org.jetbrains.kotlin.idea.util.projectStructure.version
import java.io.File
import java.net.URLClassLoader
import java.nio.file.Path
import java.util.*
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists

class K2ScratchExecutor(override val file: K2KotlinScratchFile, val project: Project, val scope: CoroutineScope) : ScratchExecutor(file) {

    val tempDir: Path by lazy {
        FileUtil.createTempDirectory("kotlin", "scratches").toPath()
    }

    override fun execute() {
        handler.onStart(file)

        val scriptFile = file.file
        val module = file.module

        scope.launch {
            val document = readAction { scriptFile.findDocument() }
            if (document != null) {
                edtWriteAction {
                    PsiDocumentManager.getInstance(project).commitDocument(document)
                    FileDocumentManager.getInstance().saveDocument(document)
                }
            }

            val result = withBackgroundProgress(project, title = KotlinJvmBundle.message("progress.title.compiling.kotlin.scratch")) {
                getJavaCommandLine(file.file, module).createProcess().awaitExit()
            }

            if (result != 0) {
                handler.error(file, "Compilation failed with code $result")
            } else {
                runCatching {
                    val explanations = runCompiledScript(scriptFile, module).map { (key, value) ->
                        val leftBracketIndex = key.indexOf("(")
                        val rightBracketIndex = key.indexOf(")")
                        val commaIndex = key.indexOf(",")

                        val offsets =
                            key.substring(leftBracketIndex + 1, commaIndex).toInt() to key.substring(commaIndex + 2, rightBracketIndex)
                                .toInt()

                        ExplainInfo(
                            key.substring(0, leftBracketIndex), offsets, value, file.getPsiFile()?.getLineNumber(offsets.second)
                        )
                    }

                    handler.handle(file, explanations, scope)
                }.onFailure {
                    handler.error(file, it.message ?: "Unknown error")
                }
            }

            handler.onFinish(file)
        }
    }

    private fun getJavaCommandLine(scriptVirtualFile: VirtualFile, module: Module?): GeneralCommandLine {
        val javaParameters =
            JavaParametersBuilder(project).withSdkFrom(module ?: ModuleUtilCore.findModuleForFile(scriptVirtualFile, project), true)
                .withMainClassName("org.jetbrains.kotlin.preloading.Preloader").build()

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

        // TODO: KTIJ-32993
        val kotlincIdeLibDirectory = File(KotlinPluginLayout.kotlincIde, "lib")
        val powerAssertLib = File(kotlincIdeLibDirectory, KotlinArtifactNames.POWER_ASSERT_COMPILER_PLUGIN)

        // TODO: KTIJ-32993
        val classPath = buildSet {
            this += ideScriptingClasses
            listOf( //KotlinArtifacts.kotlinCompiler,
                File(kotlincIdeLibDirectory, KotlinArtifactNames.KOTLIN_COMPILER), //KotlinArtifacts.kotlinStdlib,
                File(kotlincIdeLibDirectory, KotlinArtifactNames.KOTLIN_STDLIB), //KotlinArtifacts.kotlinReflect,
                File(kotlincIdeLibDirectory, KotlinArtifactNames.KOTLIN_REFLECT), //KotlinArtifacts.kotlinScriptRuntime,
                File(kotlincIdeLibDirectory, KotlinArtifactNames.KOTLIN_SCRIPT_RUNTIME), // KotlinArtifacts.trove4j,
                File(kotlincIdeLibDirectory, KotlinArtifactNames.TROVE4J), // KotlinArtifacts.kotlinDaemon,
                File(kotlincIdeLibDirectory, KotlinArtifactNames.KOTLIN_DAEMON), powerAssertLib, //KotlinArtifacts.kotlinScriptingCompiler,
                File(kotlincIdeLibDirectory, KotlinArtifactNames.KOTLIN_SCRIPTING_COMPILER), //KotlinArtifacts.kotlinScriptingCompilerImpl,
                File(kotlincIdeLibDirectory, KotlinArtifactNames.KOTLIN_SCRIPTING_COMPILER_IMPL), //KotlinArtifacts.kotlinScriptingCommon,
                File(kotlincIdeLibDirectory, KotlinArtifactNames.KOTLIN_SCRIPTING_COMMON), //KotlinArtifacts.kotlinScriptingJvm,
                File(kotlincIdeLibDirectory, KotlinArtifactNames.KOTLIN_SCRIPTING_JVM), KotlinArtifacts.jetbrainsAnnotations
            ).mapTo(this) { it.toPath().absolutePathString() }

            if (module != null) {
                addAll(JavaParametersBuilder.getModuleDependencies(module))
            }

        }.toList()

        javaParameters.classPath.add(File(kotlincIdeLibDirectory, KotlinArtifactNames.KOTLIN_PRELOADER).absolutePath)
        javaParameters.programParametersList.addAll(
            "-cp",
            File(kotlincIdeLibDirectory, KotlinArtifactNames.KOTLIN_COMPILER).absolutePath,
            "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler",
            "-cp",
            classPath.joinToString(File.pathSeparator),
            "-kotlin-home",
            KotlinPluginLayout.kotlincIde.absolutePath,
            scriptVirtualFile.path,
            "-d",
            getPathToScriptJar(scriptVirtualFile).absolutePathString(),
            "-Xplugin=${powerAssertLib.absolutePath}",
            "-script-templates",
            KotlinScratchScript::class.java.name,
            "-Xuse-fir-lt=false",
            "-Xallow-any-scripts-in-source-roots",
            "-P",
            "plugin:kotlin.scripting:disable-script-definitions-autoloading=true",
            "-P",
            "plugin:kotlin.scripting:disable-standard-script=true",
            "-P",
            "plugin:kotlin.scripting:enable-script-explanation=true"
        )

        return javaParameters.toCommandLine()
    }

    private fun runCompiledScript(scriptFile: VirtualFile, module: Module?): MutableMap<String, Any> {
        val pathToJar = getPathToScriptJar(scriptFile)
        val kotlinPluginJar = Path.of(PathUtil.getJarPathForClass(KotlinScratchScript::class.java))

        val moduleClassPath = module?.let {
            JavaParametersBuilder.getModuleDependencies(it)
        }?.mapNotNull { it.toNioPathOrNull() }?.filter {
            it.exists()
        }?.toSet() ?: emptySet()

        val urls = (moduleClassPath + listOf(
            kotlinPluginJar,
            pathToJar,
        )).map { it.toUri().toURL() }.toTypedArray()

        val classFileName = scriptFile.nameWithoutExtension.run {
            replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        }
        val classLoader = URLClassLoader.newInstance(urls)

        val results: MutableMap<String, Any> = mutableMapOf()

        val loadedClass = classLoader.loadClass(classFileName)
        loadedClass.constructors.single().newInstance(results)

        return results
    }

    fun getPathToScriptJar(scriptFile: VirtualFile): Path = tempDir.resolve(scriptFile.name.replace(".kts", ".jar"))

    override fun stop() {
        handler.onFinish(file)
    }
}