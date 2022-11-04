// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.debugger.test

import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.doWriteAction
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.ArchiveFileSystem
import com.intellij.psi.PsiManager
import org.jetbrains.kotlin.analyzer.AnalysisResult
import org.jetbrains.kotlin.caches.resolve.KotlinCacheService
import org.jetbrains.kotlin.cli.common.output.writeAllTo
import org.jetbrains.kotlin.cli.jvm.compiler.findMainClass
import org.jetbrains.kotlin.codegen.ClassBuilderFactories
import org.jetbrains.kotlin.codegen.ClassBuilderFactory
import org.jetbrains.kotlin.codegen.GenerationUtils
import org.jetbrains.kotlin.codegen.state.GenerationState
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import org.jetbrains.kotlin.config.JvmTarget
import org.jetbrains.kotlin.diagnostics.rendering.DefaultErrorMessages
import org.jetbrains.kotlin.idea.base.plugin.artifacts.TestKotlinArtifacts
import org.jetbrains.kotlin.idea.codegen.CodegenTestUtil
import org.jetbrains.kotlin.idea.resolve.ResolutionFacade
import org.jetbrains.kotlin.idea.resolve.languageVersionSettings
import org.jetbrains.kotlin.idea.test.KotlinBaseTest.TestFile
import org.jetbrains.kotlin.idea.test.KotlinCompilerStandalone
import org.jetbrains.kotlin.idea.test.testFramework.KtUsefulTestCase
import com.intellij.openapi.application.runReadAction
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.psi.KtFile
import java.io.File

open class DebuggerTestCompilerFacility(
    files: List<TestFileWithModule>,
    private val jvmTarget: JvmTarget,
    private val useIrBackend: Boolean
) {
    private val kotlinStdlibPath = TestKotlinArtifacts.kotlinStdlib.absolutePath

    private val mainFiles: TestFilesByLanguageAndPlatform
    private val libraryFiles: TestFilesByLanguageAndPlatform
    private val mavenArtifacts = mutableListOf<String>()

    init {
        val splitFiles = splitByTarget(files)
        mainFiles = splitByLanguage(splitFiles.main)
        libraryFiles = splitByLanguage(splitFiles.library)
    }

    fun compileExternalLibrary(name: String, srcDir: File, classesDir: File) {
        val libSrcPath = File(DEBUGGER_TESTDATA_PATH_BASE, "lib/$name")
        if (!libSrcPath.exists()) {
            error("Library $name does not exist")
        }

        val testFiles = libSrcPath.walk().filter { it.isFile }.toList().map {
            val path = it.toRelativeString(libSrcPath)
            TestFileWithModule(DebuggerTestModule.Jvm, path, FileUtil.loadFile(it, true))
        }

        val libraryFiles = splitByLanguage(testFiles)
        compileLibrary(libraryFiles, srcDir, classesDir)
    }

    fun addDependencies(libraryPaths: List<String>) {
        for (libraryPath in libraryPaths) {
            mavenArtifacts.add(libraryPath)
        }
    }

    private fun kotlinStdlibInMavenArtifacts() =
        mavenArtifacts.find { it.contains(Regex("""kotlin-stdlib-\d+\.\d+\.\d+(-\w+)?""")) }

    fun compileLibrary(srcDir: File, classesDir: File) {
        compileLibrary(this.libraryFiles, srcDir, classesDir)

        srcDir.refreshAndToVirtualFile()?.let { KtUsefulTestCase.refreshRecursively(it) }
        classesDir.refreshAndToVirtualFile()?.let { KtUsefulTestCase.refreshRecursively(it) }
    }

    private fun compileLibrary(
        libraryFiles: TestFilesByLanguageAndPlatform,
        srcDir: File,
        classesDir: File
    ) = with(libraryFiles) {
        resources.copy(classesDir)
        (kotlinJvm + java).copy(srcDir)

        if (kotlinStdlibInMavenArtifacts() == null)
            mavenArtifacts.add(kotlinStdlibPath)

        val options = mutableListOf("-jvm-target", jvmTarget.description)

        if (!useIrBackend) {
            options.add("-Xuse-old-backend")
        }

        if (kotlinJvm.isNotEmpty()) {
            KotlinCompilerStandalone(
                listOf(srcDir), target = classesDir,
                options = options,
                classpath = mavenArtifacts.map(::File)
            ).compile()
        }

        if (java.isNotEmpty()) {
            CodegenTestUtil.compileJava(
                java.map { File(srcDir, it.name).absolutePath },
                mavenArtifacts + classesDir.absolutePath,
                listOf("-g"),
                classesDir
            )
        }
    }

    // Returns the qualified name of the main test class.
    open fun compileTestSources(
        module: Module,
        jvmSrcDir: File,
        commonSrcDir: File,
        classesDir: File,
        libClassesDir: File
    ): String = with(mainFiles) {
        resources.copy(jvmSrcDir)
        resources.copy(classesDir) // sic!
        (kotlinJvm + java).copy(jvmSrcDir)
        kotlinCommon.forEach { testFile -> testFile.copy(commonSrcDir.resolve(testFile.module.name)) }

        lateinit var allKtFiles: List<KtFile>
        lateinit var jvmKtFiles: List<KtFile>
        val project = module.project
        doWriteAction {
            jvmKtFiles = createPsiFilesAndCollectKtFiles(kotlinJvm + java, jvmSrcDir, project)
            val commonKtFiles = kotlinCommon.groupBy { it.module }.flatMap { (module, files) ->
                createPsiFilesAndCollectKtFiles(files, commonSrcDir.resolve(module.name), project)
            }
            allKtFiles = jvmKtFiles + commonKtFiles
        }

        if (allKtFiles.isEmpty()) {
            error("No Kotlin files found")
        }

        LocalFileSystem.getInstance().refreshAndFindFileByIoFile(classesDir)
        LocalFileSystem.getInstance().refreshAndFindFileByIoFile(libClassesDir)

        lateinit var mainClassName: String

        doWriteAction {
            if (kotlinCommon.isNotEmpty()) {
                compileKotlinFilesWithCliCompiler(jvmSrcDir, commonSrcDir, classesDir)
                mainClassName = analyzeAndFindMainClass(project, jvmKtFiles)
            } else {
                mainClassName = compileKotlinFilesInIdeAndFindMainClass(project, allKtFiles, classesDir)
            }
        }

        if (java.isNotEmpty()) {
            CodegenTestUtil.compileJava(
                java.map { File(jvmSrcDir, it.name).absolutePath },
                getClasspath(module) + listOf(classesDir.absolutePath),
                listOf("-g"),
                classesDir
            )
        }

        return mainClassName
    }

    open fun compileTestSources(
        project: Project,
        srcDir: File,
        classesDir: File,
        classBuilderFactory: ClassBuilderFactory = ClassBuilderFactories.BINARIES
    ): CompilationResult = with(mainFiles) {
        val testFiles = kotlinJvm + java
        testFiles.copy(srcDir)
        LocalFileSystem.getInstance().refreshAndFindFileByIoFile(srcDir)

        lateinit var ktFiles: List<KtFile>
        doWriteAction {
            ktFiles = createPsiFilesAndCollectKtFiles(testFiles, srcDir, project)
        }

        return runReadAction {
            compileKotlinFilesInIde(project, ktFiles, classesDir, classBuilderFactory)
        }
    }

    private fun compileKotlinFilesWithCliCompiler(jvmSrcDir: File, commonSrcDir: File, classesDir: File) {
        KotlinCompilerStandalone(
            listOf(jvmSrcDir, commonSrcDir), target = classesDir,
            options = listOf(
                "-Xuse-ir=$useIrBackend",
                "-Xcommon-sources=${commonSrcDir.absolutePath}",
                "-Xmulti-platform"
            ),
            classpath = mavenArtifacts.map(::File)
        ).compile()
    }

    protected open fun analyzeAndFindMainClass(project: Project, jvmKtFiles: List<KtFile>): String {
        val resolutionFacade = KotlinCacheService.getInstance(project).getResolutionFacade(jvmKtFiles)

        val analysisResult = resolutionFacade.analyzeWithAllCompilerChecks(jvmKtFiles)
        analysisResult.throwIfError()

        return findMainClass(analysisResult.bindingContext, resolutionFacade.languageVersionSettings, jvmKtFiles)?.asString()
            ?: error("Cannot find main class name")
    }

    data class CompilationResult(
        val analysisResult: AnalysisResult,
        val generationState: GenerationState,
        val resolutionFacade: ResolutionFacade
    )

    private fun compileKotlinFilesInIde(
        project: Project,
        files: List<KtFile>,
        classesDir: File,
        classBuilderFactory: ClassBuilderFactory = ClassBuilderFactories.BINARIES
    ): CompilationResult {
        val resolutionFacade = KotlinCacheService.getInstance(project).getResolutionFacadeWithForcedPlatform(files, JvmPlatforms.unspecifiedJvmPlatform)

        val analysisResult = try {
            resolutionFacade.analyzeWithAllCompilerChecks(files)
        } catch (_: ProcessCanceledException) {
            // allow module's descriptors update due to dynamic loading of Scripting Support Libraries for .kts files
            resolutionFacade.analyzeWithAllCompilerChecks(files)
        }
        analysisResult.throwIfError()

        val configuration = CompilerConfiguration()
        configuration.put(JVMConfigurationKeys.JVM_TARGET, jvmTarget)
        configuration.put(JVMConfigurationKeys.IR, useIrBackend)
        configuration.put(JVMConfigurationKeys.DO_NOT_CLEAR_BINDING_CONTEXT, true)

        val state = GenerationUtils.generateFiles(project, files, configuration, classBuilderFactory, analysisResult) {
            generateDeclaredClassFilter(GenerationState.GenerateClassFilter.GENERATE_ALL)
        }

        val extraDiagnostics = state.collectedExtraJvmDiagnostics
        if (!extraDiagnostics.isEmpty()) {
            val compoundMessage = extraDiagnostics.joinToString("\n") { DefaultErrorMessages.render(it) }
            error("One or more errors occurred during code generation: \n$compoundMessage")
        }

        state.factory.writeAllTo(classesDir)

        return CompilationResult(analysisResult, state, resolutionFacade)
    }

    protected open fun compileKotlinFilesInIdeAndFindMainClass(project: Project, files: List<KtFile>, classesDir: File): String {
        val (analysisResult, _, resolutionFacade) = compileKotlinFilesInIde(project, files, classesDir)
        return findMainClass(analysisResult.bindingContext, resolutionFacade.languageVersionSettings, files)?.asString()
            ?: error("Cannot find main class name")
    }

    private fun createPsiFilesAndCollectKtFiles(testFiles: List<TestFile>, srcDir: File, project: Project): List<KtFile> {
        val ktFiles = mutableListOf<KtFile>()
        for (file in testFiles) {
            val ioFile = File(srcDir, file.name)
            val virtualFile = ioFile.refreshAndToVirtualFile() ?: error("Cannot find a VirtualFile instance for file $file")
            val psiFile = PsiManager.getInstance(project).findFile(virtualFile) ?: continue

            if (psiFile is KtFile) {
                ktFiles += psiFile
            }
        }

        return ktFiles
    }

    private fun getClasspath(module: Module): List<String> {
        val moduleRootManager = ModuleRootManager.getInstance(module)
        val classpath = moduleRootManager.orderEntries.filterIsInstance<LibraryOrderEntry>()
            .flatMap { it.library?.rootProvider?.getFiles(OrderRootType.CLASSES)?.asList().orEmpty() }

        val paths = mutableListOf<String>()
        for (entry in classpath) {
            val fileSystem = entry.fileSystem
            if (fileSystem is ArchiveFileSystem) {
                val localFile = fileSystem.getLocalByEntry(entry) ?: continue
                paths += localFile.path
            } else if (fileSystem is LocalFileSystem) {
                paths += entry.path
            }
        }

        return paths
    }
}

internal fun File.refreshAndToVirtualFile(): VirtualFile? = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(this)

private fun List<TestFile>.copy(destination: File) {
    for (file in this) {
        file.copy(destination)
    }
}

private fun TestFile.copy(destination: File) {
    val target = File(destination, name)
    target.parentFile.mkdirs()
    target.writeText(content)
}

class TestFilesByTarget(val main: List<TestFileWithModule>, val library: List<TestFileWithModule>)

class TestFilesByLanguageAndPlatform(
    val kotlinJvm: List<TestFileWithModule>,
    val kotlinCommon: List<TestFileWithModule>,
    val java: List<TestFileWithModule>,
    val resources: List<TestFileWithModule>
)

private fun splitByTarget(files: List<TestFileWithModule>): TestFilesByTarget {
    val main = mutableListOf<TestFileWithModule>()
    val lib = mutableListOf<TestFileWithModule>()

    for (file in files) {
        val container = if (file.name.startsWith("lib/") || file.name.startsWith("customLib/")) lib else main
        container += file
    }

    return TestFilesByTarget(main = main, library = lib)
}

private fun splitByLanguage(files: List<TestFileWithModule>): TestFilesByLanguageAndPlatform {
    val kotlinJvm = mutableListOf<TestFileWithModule>()
    val kotlinCommon = mutableListOf<TestFileWithModule>()
    val java = mutableListOf<TestFileWithModule>()
    val resources = mutableListOf<TestFileWithModule>()

    for (file in files) {
        @Suppress("MoveVariableDeclarationIntoWhen")
        val extension = file.name.substringAfterLast(".", missingDelimiterValue = "")

        val container = when (extension) {
            "kt", "kts" ->
                when (file.module) {
                    is DebuggerTestModule.Common -> kotlinCommon
                    is DebuggerTestModule.Jvm -> kotlinJvm
                }
            "java" -> java
            else -> resources
        }

        container += file
    }

    return TestFilesByLanguageAndPlatform(kotlinJvm = kotlinJvm, kotlinCommon = kotlinCommon, java = java, resources = resources)
}
