// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.debugger.test

import com.intellij.openapi.application.runReadAction
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
import com.intellij.testFramework.IndexingTestUtil
import org.jetbrains.kotlin.analyzer.AnalysisResult
import org.jetbrains.kotlin.caches.resolve.KotlinCacheService
import org.jetbrains.kotlin.cli.jvm.compiler.findMainClass
import org.jetbrains.kotlin.config.*
import org.jetbrains.kotlin.idea.base.plugin.artifacts.TestKotlinArtifacts
import org.jetbrains.kotlin.idea.codegen.CodegenTestUtil
import org.jetbrains.kotlin.idea.resolve.languageVersionSettings
import org.jetbrains.kotlin.idea.test.KotlinBaseTest.TestFile
import org.jetbrains.kotlin.idea.test.KotlinCompilerStandalone
import org.jetbrains.kotlin.idea.test.testFramework.KtUsefulTestCase
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.psi.KtFile
import java.io.File
import java.nio.file.Path
import kotlin.io.path.absolutePathString

data class TestCompileConfiguration(
    val lambdasGenerationScheme: JvmClosureGenerationScheme,
    val languageVersion: LanguageVersion?,
    val enabledLanguageFeatures: Collection<LanguageFeature>,
    val useInlineScopes: Boolean,
)

open class DebuggerTestCompilerFacility(
    private val project: Project,
    files: List<TestFileWithModule>,
    private val jvmTarget: JvmTarget,
    private val compileConfig: TestCompileConfiguration,
) {
    private val kotlinStdlibPath = TestKotlinArtifacts.kotlinStdlib.absolutePath

    protected val mainFiles: TestFilesByLanguageAndPlatform
    private val libraryFiles: TestFilesByLanguageAndPlatform
    private val mavenArtifacts = mutableListOf<String>()
    private val compilerPlugins = mutableListOf<String>()

    init {
        if (compileConfig.languageVersion?.usesK2 == true && compileConfig.lambdasGenerationScheme != JvmClosureGenerationScheme.INDY) {
            throw IllegalArgumentException("There is no point in test K2 with old lambdas generation scheme")
        }
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
            TestFileWithModule(DebuggerTestModule.Jvm.Default, path, FileUtil.loadFile(it, true))
        }

        val libraryFiles = splitByLanguage(testFiles)
        compileLibrary(libraryFiles, srcDir, classesDir)
    }

    fun addDependencies(libraryPaths: List<String>) {
        for (libraryPath in libraryPaths) {
            mavenArtifacts.add(libraryPath)
        }
    }

    fun addCompilerPlugin(pluginPaths: List<Path>) {
        pluginPaths.forEach { path ->
            compilerPlugins.add(path.absolutePathString())
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
        options.addAll(getCompilerOptionsCommonForLibAndSource())

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
        classesDir.refreshAndToVirtualFile()?.let { KtUsefulTestCase.refreshRecursively(it) }
    }

    fun compileTestSourcesWithCli(
        module: Module,
        jvmSrcDir: File,
        commonSrcDir: File,
        scriptSrcDir: File,
        classesDir: File,
        libClassesDir: File
    ) = with(mainFiles) {
        resources.copy(jvmSrcDir)
        resources.copy(classesDir) // sic!
        (kotlinJvm + java).copy(jvmSrcDir)
        kotlinScripts.copy(scriptSrcDir)
        kotlinCommon.forEach { testFile -> testFile.copy(commonSrcDir.resolve(testFile.module.name)) }

        LocalFileSystem.getInstance().refreshAndFindFileByIoFile(classesDir)
        LocalFileSystem.getInstance().refreshAndFindFileByIoFile(libClassesDir)
        IndexingTestUtil.waitUntilIndexesAreReady(project)

        if (kotlinJvm.isNotEmpty() || kotlinCommon.isNotEmpty()) {
            val options = getCompileOptionsForMainSources(jvmSrcDir, commonSrcDir)
            doWriteAction {
                compileKotlinFilesWithCliCompiler(jvmSrcDir, commonSrcDir, classesDir, libClassesDir, options)
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
    }

    fun creatKtFiles(jvmSrcDir: File, commonSrcDir: File, scriptsSrcDir: File): TestSourcesKtFiles = with(mainFiles) {
        return doWriteAction<TestSourcesKtFiles> {
            val jvmKtFiles = createPsiFilesAndCollectKtFiles(kotlinJvm + java, jvmSrcDir)
            val commonKtFiles = kotlinCommon.groupBy { it.module }.flatMap { (module, files) ->
                createPsiFilesAndCollectKtFiles(files, commonSrcDir.resolve(module.name))
            }
            val scriptKtFiles = createPsiFilesAndCollectKtFiles(kotlinScripts, scriptsSrcDir)
            val allKtFiles = jvmKtFiles + commonKtFiles + scriptKtFiles
            if (allKtFiles.isEmpty()) {
                error("No Kotlin files found")
            }
            TestSourcesKtFiles(jvmKtFiles, commonKtFiles, scriptKtFiles)
        }
    }

    private fun compileKotlinFilesWithCliCompiler(
        jvmSrcDir: File, commonSrcDir: File, classesDir: File,
        libClassesDir: File, options: List<String>,
    ) {
        KotlinCompilerStandalone(
            listOf(jvmSrcDir, commonSrcDir), target = classesDir,
            options = options,
            classpath = mavenArtifacts.map(::File) + libClassesDir,
            compileKotlinSourcesBeforeJava = false,
        ).compile()
    }

    fun getCompilerPlugins(): List<String> = compilerPlugins

    protected open fun getCompileOptionsForMainSources(jvmSrcDir: File, commonSrcDir: File): List<String> {
        return getCompilerOptionsCommonForLibAndSource()
    }

    private fun getCompilerOptionsCommonForLibAndSource(): List<String> {
        val options = mutableListOf(
            "-Xlambdas=${compileConfig.lambdasGenerationScheme.description}",
            "-Xcontext-receivers",
        )
        if (compileConfig.languageVersion != null) {
            options.add("-language-version=${compileConfig.languageVersion}")
        }
        if (compileConfig.useInlineScopes) {
            options.add("-Xuse-inline-scopes-numbers")
        }

        if (compilerPlugins.isNotEmpty()) {
            options.add("-Xplugin=${compilerPlugins.joinToString(",")}")
        }

        options.addAll(compileConfig.enabledLanguageFeatures.map { "-XXLanguage:+$it" })
        return options
    }

    open fun analyzeSources(ktFiles: List<KtFile>): Pair<LanguageVersionSettings, AnalysisResult> {
        return runReadAction {
            val resolutionFacade = KotlinCacheService.getInstance(project)
                .getResolutionFacadeWithForcedPlatform(ktFiles, JvmPlatforms.unspecifiedJvmPlatform)
            val analysisResult = try {
                resolutionFacade.analyzeWithAllCompilerChecks(ktFiles)
            } catch (_: ProcessCanceledException) {
                // allow module's descriptors update due to dynamic loading of Scripting Support Libraries for .kts files
                resolutionFacade.analyzeWithAllCompilerChecks(ktFiles)
            }
            analysisResult.throwIfError()
            resolutionFacade.languageVersionSettings to analysisResult
        }
    }

    // Returns the qualified name of the main test class.
    fun analyzeAndFindMainClass(jvmKtFiles: List<KtFile>): String {
        return runReadAction {
            val (languageVersionSettings, analysisResult) = analyzeSources(jvmKtFiles)
            findMainClass(analysisResult.bindingContext, languageVersionSettings, jvmKtFiles)?.asString()
                ?: error("Cannot find main class name")
        }
    }

    private fun createPsiFilesAndCollectKtFiles(testFiles: List<TestFile>, srcDir: File): List<KtFile> {
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
    ?.also { IndexingTestUtil.waitUntilIndexesAreReadyInAllOpenedProjects() }

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
    val kotlinScripts: List<TestFileWithModule>,
    val java: List<TestFileWithModule>,
    val resources: List<TestFileWithModule>
)

class TestSourcesKtFiles(val jvmKtFiles: List<KtFile>, val commonKtFiles: List<KtFile>, val scriptKtFiles: List<KtFile>)

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
    val kotlinScripts = mutableListOf<TestFileWithModule>()
    val java = mutableListOf<TestFileWithModule>()
    val resources = mutableListOf<TestFileWithModule>()

    for (file in files) {
        @Suppress("MoveVariableDeclarationIntoWhen")
        val extension = file.name.substringAfterLast(".", missingDelimiterValue = "")

        val container = when (extension) {
            "kt" ->
                when (file.module) {
                    is DebuggerTestModule.Common -> kotlinCommon
                    is DebuggerTestModule.Jvm -> kotlinJvm
                }

            "kts" -> kotlinScripts
            "java" -> java
            else -> resources
        }

        container += file
    }

    return TestFilesByLanguageAndPlatform(
        kotlinJvm = kotlinJvm, kotlinCommon = kotlinCommon,
        kotlinScripts = kotlinScripts,
        java = java, resources = resources
    )
}
