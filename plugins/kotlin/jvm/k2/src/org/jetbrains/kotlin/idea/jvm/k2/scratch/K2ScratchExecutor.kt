// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.jvm.k2.scratch

import com.intellij.execution.JavaParametersBuilder
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.findDocument
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.util.progress.reportSequentialProgress
import com.intellij.psi.PsiDocumentManager
import com.intellij.util.PathUtil
import com.intellij.util.io.awaitExit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.kotlin.idea.base.plugin.KotlinBasePluginBundle
import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifactConstants.KOTLIN_DIST_LOCATION_PREFIX_PATH
import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifactConstants.KOTLIN_MAVEN_GROUP_ID
import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifactConstants.OLD_KOTLIN_DIST_ARTIFACT_ID
import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifactNames
import org.jetbrains.kotlin.idea.base.psi.getLineNumber
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinArtifactsDownloader.downloadArtifactForIdeFromSources
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinMavenUtils
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinPluginLayout
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinPluginLayoutMode
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinPluginLayoutModeProvider
import org.jetbrains.kotlin.idea.compiler.configuration.downloadAtomically
import org.jetbrains.kotlin.idea.core.script.scratch.definition.KOTLIN_SCRATCH_EXPLAIN_FILE
import org.jetbrains.kotlin.idea.core.script.scratch.definition.KotlinScratchExplainScript
import org.jetbrains.kotlin.idea.core.script.scratch.definition.KotlinScratchPlainScript
import org.jetbrains.kotlin.idea.jvm.shared.KotlinJvmBundle
import org.jetbrains.kotlin.idea.jvm.shared.scratch.ScratchExecutor
import org.jetbrains.kotlin.idea.jvm.shared.scratch.output.ExplainInfo
import org.jetbrains.kotlin.idea.jvm.shared.scratch.output.ScratchOutput
import org.jetbrains.kotlin.idea.jvm.shared.scratch.output.ScratchOutputType
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists
import kotlin.io.path.readLines

private val log = Logger.getInstance(K2ScratchExecutor::class.java)

class K2ScratchExecutor(override val scratchFile: K2KotlinScratchFile, val project: Project, val scope: CoroutineScope) :
    ScratchExecutor(scratchFile) {
    override fun execute() {
        if (scratchFile.jdk == null) {
            handler.error(scratchFile, KotlinJvmBundle.message("scratch.no.jdk.selected"))
            return
        }

        handler.onStart(scratchFile)

        scope.launch {
            try {
                processExecution(scratchFile.virtualFile)
            } catch (e: Exception) {
                handler.error(scratchFile, e.message ?: "Unknown error")
            } finally {
                handler.onFinish(scratchFile)
            }
        }
    }

    private suspend fun processExecution(scriptFile: VirtualFile) {
        val document = readAction { scriptFile.findDocument() }
        if (document == null) {
            handler.error(scratchFile, "Cannot find document: ${scriptFile.path}")
            return
        }

        edtWriteAction {
            PsiDocumentManager.getInstance(project).commitDocument(document)
            FileDocumentManager.getInstance().saveDocument(document)
        }
        val isExplainEnabled = scratchFile.options.isExplainEnabled

        val (code, stdout, stderr) = withBackgroundProgress(
            project, title = KotlinJvmBundle.message("progress.title.compiling.kotlin.scratch")
        ) {
            val (scratchCompilerHome, distJar) = reportSequentialProgress { reporter ->
                reporter.itemStep(KotlinBasePluginBundle.message("progress.text.kotlin.scratch.compiler.prepare")) {
                    getKotlincIdeScratchResolution()
                }
            }

            val scratchCompilerJar = scratchCompilerArtifact(scratchCompilerHome, KotlinArtifactNames.KOTLIN_COMPILER)
            log.info("Using scratch compiler: home=$scratchCompilerHome, compilerJar=$scratchCompilerJar, distJar=$distJar")

            val process = getJavaCommandLine(
                scratchFile.virtualFile,
                scratchFile.module,
                scratchCompilerHome,
                isExplainEnabled,
            ).createProcess()
            process.awaitExit()
            val stdout = withContext(Dispatchers.IO) {
                process.inputStream.bufferedReader().use { it.readText() }
            }
            val stderr = withContext(Dispatchers.IO) {
                process.errorStream.bufferedReader().use { it.readText() }
            }

            CompilationResult(process.exitValue(), stdout, stderr)
        }

        if (code == 0) {
            if (stdout.isNotEmpty()) {
                handler.handle(scratchFile, ScratchOutput(stdout, ScratchOutputType.OUTPUT))
            }

            if (isExplainEnabled) {
                handler.handle(scratchFile, readExplanations(scriptFile), scope)
            }
        } else if (!scratchFile.options.isInteractiveMode) {
            handler.error(scratchFile, "Compilation failed: $stderr")
        }
    }

    private fun readExplanations(scriptFile: VirtualFile): List<ExplainInfo> {
        if (!Files.exists(scriptFile.explainFilePath)) return emptyList()

        return scriptFile.explainFilePath.readLines().associate {
            it.substringBefore('=', "") to unescapeExplainValue(it.substringAfter('='))
        }.filterKeys { it.isNotBlank() }.map { (key, value) ->
            val leftBracketIndex = key.indexOf("(")
            val rightBracketIndex = key.indexOf(")")
            val commaIndex = key.indexOf(",")

            val offsets =
                key.substring(leftBracketIndex + 1, commaIndex).trim().toInt() to key.substring(commaIndex + 1, rightBracketIndex)
                    .trim().toInt()

            ExplainInfo(
                key.substring(0, leftBracketIndex), offsets, value, scratchFile.getPsiFile()?.getLineNumber(offsets.first)
            )
        }
    }

    private fun getJavaCommandLine(
        scriptVirtualFile: VirtualFile,
        module: Module?,
        scratchCompilerHome: Path,
        isExplainEnabled: Boolean,
    ): GeneralCommandLine {
        val javaParameters =
            JavaParametersBuilder(project)
                .withMainClassName("org.jetbrains.kotlin.preloading.Preloader")
                .build()
        javaParameters.jdk = checkNotNull(scratchFile.jdk) {
            "Scratch JDK must be selected before execution; guarded by execute() and RunScratchActionK2.update()."
        }

        javaParameters.charset = null
        if (isExplainEnabled) {
            javaParameters.vmParametersList.add("-D$KOTLIN_SCRATCH_EXPLAIN_FILE=${scriptVirtualFile.explainFilePath}")
        }

        val classPath = buildSet {
            add(ideaScriptingJar)
            addAll(requiredKotlinArtifacts(scratchCompilerHome, isExplainEnabled))
            if (module != null) addAll(JavaParametersBuilder.getModuleDependencies(module))
        }

        javaParameters.classPath.add(
            scratchCompilerArtifact(
                scratchCompilerHome, KotlinArtifactNames.KOTLIN_PRELOADER
            ).absolutePathString()
        )
        val scriptTemplate = if (isExplainEnabled) {
            KotlinScratchExplainScript::class.java
        } else {
            KotlinScratchPlainScript::class.java
        }

        @Suppress("IO_FILE_USAGE")
        val programParameters = buildList {
            addAll(
                listOf(
                    "-cp",
                    scratchCompilerArtifact(scratchCompilerHome, KotlinArtifactNames.KOTLIN_COMPILER).absolutePathString(),
                    "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler",
                    "-cp",
                    classPath.joinToString(java.io.File.pathSeparator),
                    "-kotlin-home",
                    scratchCompilerHome.absolutePathString(),
                    "-script",
                    scriptVirtualFile.path,
                )
            )

            if (isExplainEnabled) {
                val powerAssertPluginPath = scratchCompilerArtifact(
                    scratchCompilerHome, KotlinArtifactNames.POWER_ASSERT_COMPILER_PLUGIN
                ).absolutePathString()
                add("-Xplugin=$powerAssertPluginPath")
            }

            addAll(
                listOf(
                    "-P",
                    "plugin:kotlin.scripting:script-templates=${scriptTemplate.name}",
                    "-Xuse-fir-lt=false",
                    "-Xallow-any-scripts-in-source-roots",
                    "-P",
                    "plugin:kotlin.scripting:disable-script-definitions-autoloading=true",
                    "-P",
                    "plugin:kotlin.scripting:disable-standard-script=true",
                )
            )

            if (isExplainEnabled) {
                add("-P")
                add("plugin:kotlin.scripting:enable-script-explanation=true")
            }
        }

        javaParameters.programParametersList.addAll(programParameters)

        val commandLine = javaParameters.toCommandLine()
        log.info("commandLine=${commandLine.commandLineString}")

        return commandLine
    }

    private fun requiredKotlinArtifacts(scratchCompilerHome: Path, includePowerAssert: Boolean): List<String> =
        kotlincIdeScratchClasspathArtifactFileNames
            .filter { includePowerAssert || it != KotlinArtifactNames.POWER_ASSERT_COMPILER_PLUGIN }
            .map { scratchCompilerArtifact(scratchCompilerHome, it) }
            .filter(Files::exists).map(Path::absolutePathString)

    private fun scratchCompilerArtifact(scratchCompilerHome: Path, fileName: String): Path =
        scratchCompilerHome.resolve("lib").resolve(fileName)

    private val ideaScriptingJar by lazy { PathUtil.getJarPathForClass(KotlinScratchExplainScript::class.java) }

    private val explainScratchesDirectory: Path by lazy {
        FileUtilRt.createTempDirectory("kotlin-scratches-explain", null, true).toPath()
    }

    private val VirtualFile.explainFilePath: Path
        get() = explainScratchesDirectory.resolve(this.name.replace(".kts", ".txt"))

    override fun stop() {
        handler.onFinish(scratchFile)
    }

    private fun unescapeExplainValue(value: String): String =
        value.replace("\\\\", "\u0000").replace("\\n", "\n").replace("\\r", "\r").replace("\u0000", "\\")

    private data class CompilationResult(
        val code: Int,
        val stdout: String,
        val stderr: String,
    )

    private suspend fun getKotlincIdeScratchResolution(): ScratchCompilerResolution =
        when (KotlinPluginLayoutModeProvider.kotlinPluginLayoutMode) {
            KotlinPluginLayoutMode.SOURCES -> withScratchCompilerFallback("Failed to prepare scratch compiler artifacts from sources.") {
                resolveKotlincIdeScratchHomeFromSources()
            }

            KotlinPluginLayoutMode.INTELLIJ -> withScratchCompilerFallback("Failed to download scratch compiler artifacts.") {
                resolveKotlincIdeScratchHomeInProduction()
            }

            KotlinPluginLayoutMode.LSP -> error("LSP doesn't not include kotlinc")
        }

    private suspend fun withScratchCompilerFallback(
        errorMessage: String,
        block: suspend () -> ScratchCompilerResolution,
    ): ScratchCompilerResolution = try {
        block()
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        log.warn("$errorMessage Falling back to bundled JPS compiler at ${KotlinPluginLayout.kotlincPath}.", e)
        ScratchCompilerResolution(compilerHome = KotlinPluginLayout.kotlincPath, distJar = null)
    }

    private suspend fun resolveKotlincIdeScratchHomeInProduction(): ScratchCompilerResolution {
        val distJar = withContext(Dispatchers.IO) { downloadArtifactForIde() } ?: error("Can't download dist")
        val unpackedDistDir = KOTLIN_DIST_LOCATION_PREFIX_PATH.resolve(kotlincIdeScratchDirectoryName)
        val compilerHome = withContext(Dispatchers.IO) {
            extractKotlincIdeScratchHome(distJar = distJar, targetDir = unpackedDistDir)
        }

        return ScratchCompilerResolution(compilerHome, distJar)
    }

    private suspend fun resolveKotlincIdeScratchHomeFromSources(): ScratchCompilerResolution {
        val distJar = withContext(Dispatchers.IO) {
            val version = KotlinMavenUtils.findLibraryVersion("kotlinc_kotlin_compiler_cli.xml")
            downloadArtifactForIdeFromSources(version)
        } ?: error("Can't download dist")

        val unpackedDistDir = KOTLIN_DIST_LOCATION_PREFIX_PATH.resolve(
            "$kotlincIdeScratchDirectoryName-from-sources"
        )

        val compilerHome = withContext(Dispatchers.IO) {
            extractKotlincIdeScratchHome(distJar = distJar, targetDir = unpackedDistDir)
        }

        return ScratchCompilerResolution(compilerHome, distJar)
    }

    private suspend fun downloadArtifactForIde(): Path? {
        val artifactId = OLD_KOTLIN_DIST_ARTIFACT_ID
        val version = KotlinPluginLayout.ideCompilerVersion.rawVersion
        val suffix = ".jar"
        return KotlinMavenUtils.findArtifact(KOTLIN_MAVEN_GROUP_ID, artifactId, version, suffix)
            ?: findOpenedProjectLocalKotlinSnapshotArtifact(
                artifactId = artifactId,
                version = version,
                suffix = suffix
            )
            ?: downloadMavenArtifactDirectly(
                artifactId = artifactId,
                version = version,
                suffix = suffix,
                targetDirectory = PathManager.getSystemDir().resolve(KOTLIN_DIST_LOCATION_PREFIX_PATH).resolve("downloads"),
            )
    }

    private suspend fun downloadMavenArtifactDirectly(
        artifactId: String,
        version: String,
        suffix: String,
        targetDirectory: Path,
    ): Path? {
        val fileName = "$artifactId-$version$suffix"
        val artifact = targetDirectory.resolve(fileName).also { Files.createDirectories(it.parent) }
        val groupPath = KOTLIN_MAVEN_GROUP_ID.replace(".", "/")
        if (!artifact.exists()) {
            val artifactCoordinates = "$groupPath/$artifactId/$version/$fileName"
            downloadAtomically(artifact, artifactCoordinates)

            check(artifact.exists()) { "$artifact should be downloaded" }
        }

        return artifact
    }

    private fun findOpenedProjectLocalKotlinSnapshotArtifact(
        artifactId: String,
        version: String,
        suffix: String,
    ): Path? {
        val artifactRelativePath = Paths.get(KOTLIN_MAVEN_GROUP_ID.replace(".", "/"), artifactId, version, "$artifactId-$version$suffix")
        return (sequenceOf(project.basePath?.let(Paths::get)) + sequenceOf(Paths.get(PathManager.getHomePath())))
            .filterNotNull()
            .flatMap { generateSequence(it) { p -> p.parent } }
            .distinct()
            .flatMap { root -> sequenceOf(root.resolve("community/lib/kotlin-snapshot"), root.resolve("lib/kotlin-snapshot")) }
            .map { it.resolve(artifactRelativePath) }
            .firstOrNull(Files::exists)
    }

    private data class ScratchCompilerResolution(
        val compilerHome: Path,
        val distJar: Path?,
    )
}
