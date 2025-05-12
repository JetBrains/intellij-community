// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.debugger.test

import com.intellij.debugger.DebuggerManagerEx
import com.intellij.debugger.DefaultDebugEnvironment
import com.intellij.debugger.MockConfiguration
import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.impl.DebuggerSession
import com.intellij.debugger.impl.DescriptorTestCase
import com.intellij.debugger.impl.GenericDebuggerRunnerSettings
import com.intellij.debugger.impl.OutputChecker
import com.intellij.debugger.settings.DebuggerSettings
import com.intellij.execution.ExecutionTestCase
import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessListener
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiFile
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.util.ThrowableRunnable
import com.intellij.util.containers.addIfNotNull
import com.intellij.xdebugger.XDebugSession
import org.jetbrains.kotlin.config.*
import org.jetbrains.kotlin.fileClasses.JvmFileClassUtil
import org.jetbrains.kotlin.idea.base.codeInsight.KotlinMainFunctionDetector
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.base.plugin.artifacts.TestKotlinArtifacts
import org.jetbrains.kotlin.idea.base.psi.classIdIfNonLocal
import org.jetbrains.kotlin.idea.base.test.IgnoreTests
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinCommonCompilerArgumentsHolder
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinCompilerSettings
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinPluginLayout
import org.jetbrains.kotlin.idea.debugger.evaluate.KotlinEvaluator
import org.jetbrains.kotlin.idea.debugger.test.preference.*
import org.jetbrains.kotlin.idea.debugger.test.util.BreakpointCreator
import org.jetbrains.kotlin.idea.debugger.test.util.KotlinOutputChecker
import org.jetbrains.kotlin.idea.debugger.test.util.LogPropagator
import org.jetbrains.kotlin.idea.test.*
import org.jetbrains.kotlin.idea.test.KotlinBaseTest.TestFile
import org.jetbrains.kotlin.idea.test.KotlinTestUtils.*
import org.jetbrains.kotlin.idea.test.TestFiles.TestFileFactory
import org.jetbrains.kotlin.idea.test.TestFiles.createTestFiles
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtTreeVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.junit.ComparisonFailure
import java.io.File

internal const val KOTLIN_LIBRARY_NAME = "KotlinJavaRuntime"
internal const val TEST_LIBRARY_NAME = "TestLibrary"
internal const val COMMON_SOURCES_DIR = "commonSrc"
internal const val SCRIPT_SOURCES_DIR = "scripts"
internal const val JVM_MODULE_NAME_START = "jvm"

abstract class KotlinDescriptorTestCase : DescriptorTestCase(),
                                          IgnorableTestCase,
                                          ExpectedPluginModeProvider {

    private lateinit var testAppDirectory: File
    private lateinit var jvmSourcesOutputDirectory: File
    private lateinit var commonSourcesOutputDirectory: File
    private lateinit var scriptSourcesOutputDirectory: File

    private lateinit var librarySrcDirectory: File
    private lateinit var libraryOutputDirectory: File

    protected lateinit var sourcesKtFiles: TestSourcesKtFiles

    override var ignoreIsPassedCallback: (() -> Nothing)? = null

    override fun getTestAppPath(): String = testAppDirectory.absolutePath
    override fun getTestProjectJdk() = PluginTestCaseBase.fullJdk()

    private fun systemLogger(message: String) = println(message, ProcessOutputTypes.SYSTEM)

    private var breakpointCreator: BreakpointCreator? = null
    private var logPropagator: LogPropagator? = null

    private var oldValues: OldValuesStorage? = null
    private val vmAttacher = VmAttacher.getInstance()

    override fun runBare(testRunnable: ThrowableRunnable<Throwable>) {
        testAppDirectory = tmpDir("debuggerTestSources")
        jvmSourcesOutputDirectory = File(testAppDirectory, ExecutionTestCase.SOURCES_DIRECTORY_NAME).apply { mkdirs() }
        commonSourcesOutputDirectory = File(testAppDirectory, COMMON_SOURCES_DIR).apply { mkdirs() }
        scriptSourcesOutputDirectory = File(testAppDirectory, SCRIPT_SOURCES_DIR).apply { mkdirs() }

        librarySrcDirectory = File(testAppDirectory, "libSrc").apply { mkdirs() }
        libraryOutputDirectory = File(testAppDirectory, "lib").apply { mkdirs() }

        if (InTextDirectivesUtils.isIgnoredForK2Code(compileWithK2, dataFile())) {
            println("Test is skipped for K2 code")
            return
        }

        IgnoreTests.runTestIfNotDisabledByFileDirective(
            dataFile().toPath(),
            when (pluginMode) {
                KotlinPluginMode.K1 -> IgnoreTests.DIRECTIVES.IGNORE_K1
                KotlinPluginMode.K2 -> getK2IgnoreDirective()
            },
            directivePosition = IgnoreTests.DirectivePosition.LAST_LINE_IN_FILE
        ) {
            super.runBare(testRunnable)
        }
    }

    protected open fun getK2IgnoreDirective(): String = IgnoreTests.DIRECTIVES.IGNORE_K2

    protected open val compileWithK2: Boolean get() = false

    protected open val useInlineScopes: Boolean get() = false

    override fun setUp() {
        setUpWithKotlinPlugin { super.setUp() }

        KotlinEvaluator.LOG_COMPILATIONS = true
        logPropagator = LogPropagator(::systemLogger).apply { attach() }
        atDebuggerTearDown { logPropagator = null }
        atDebuggerTearDown { logPropagator?.detach() }
        atDebuggerTearDown { detachLibraries() }
        atDebuggerTearDown { oldValues = null }
        atDebuggerTearDown { invokeAndWaitIfNeeded { oldValues?.revertValues() } }
        atDebuggerTearDown { KotlinEvaluator.LOG_COMPILATIONS = false }
        atDebuggerTearDown { restoreIdeCompilerSettings() }

        vmAttacher.setUp()
        atDebuggerTearDown { vmAttacher.tearDown() }
    }

    override fun logAllCommands(): Boolean = false

    protected fun dataFile(fileName: String): File = File(getTestDataPath(), fileName)

    protected fun dataFile(): File = dataFile(fileName())

    protected open fun fileName(): String = getTestDataFileName(this::class.java, this.name) ?: (getTestName(false) + ".kt")

    fun getTestDataPath(): String = getTestsRoot(this::class.java)

    open fun lambdasGenerationScheme() = JvmClosureGenerationScheme.CLASS

    protected open fun configureProjectByTestFiles(testFiles: List<TestFileWithModule>, testAppDirectory: File) {
    }

    protected open fun createDebuggerTestCompilerFacility(
        testFiles: TestFiles,
        jvmTarget: JvmTarget,
        compileConfig: TestCompileConfiguration,
    ) = DebuggerTestCompilerFacility(project, testFiles, jvmTarget, compileConfig)

    @Suppress("UNUSED_PARAMETER")
    open fun doTest(unused: String) {
        val wholeFile = dataFile()
        val wholeFileContents = FileUtil.loadFile(wholeFile, true)

        val testFiles = createTestFiles(wholeFile, wholeFileContents)
        configureProjectByTestFiles(testFiles, testAppDirectory)

        val preferences = DebuggerPreferences(myProject, wholeFileContents)
        configureRegistry(preferences)

        invokeAndWaitIfNeeded {
            oldValues = SettingsMutators.mutate(preferences)
        }

        val rawJvmTarget = preferences[DebuggerPreferenceKeys.JVM_TARGET]
        val jvmTarget = JvmTarget.fromString(rawJvmTarget) ?: error("Invalid JVM target value: $rawJvmTarget")

        val rawJvmDefaultMode = preferences[DebuggerPreferenceKeys.JVM_DEFAULT_MODE]
        val jvmDefaultMode =
            if (rawJvmDefaultMode == DebuggerPreferenceKeys.JVM_DEFAULT_MODE.defaultValue)
                null
            else
                JvmDefaultMode.fromStringOrNull(rawJvmDefaultMode) ?: error("Invalid JVM default mode value: $rawJvmDefaultMode")

        val languageVersion = chooseLanguageVersionForCompilation(compileWithK2)

        val enabledLanguageFeatures = preferences[DebuggerPreferenceKeys.ENABLED_LANGUAGE_FEATURE]
            .map { LanguageFeature.fromString(it) ?: error("Not found language feature $it") }

        val compilerFacility = createDebuggerTestCompilerFacility(
            testFiles, jvmTarget,
            TestCompileConfiguration(
                lambdasGenerationScheme(),
                languageVersion,
                enabledLanguageFeatures,
                useInlineScopes,
                jvmDefaultMode,
            )
        )

        updateIdeCompilerSettingsForEvaluator(languageVersion, enabledLanguageFeatures, compilerFacility.getCompilerPlugins())

        compileLibrariesAndTestSources(preferences, compilerFacility)

        val mainClassName = getMainClassName(compilerFacility)
        breakpointCreator = BreakpointCreator(
            project,
            ::systemLogger,
            preferences
        ).apply { createAdditionalBreakpoints(wholeFileContents) }

        createLocalProcess(mainClassName)
        doMultiFileTest(testFiles, preferences)
    }

    private fun configureRegistry(preferences: DebuggerPreferences) {
        val registrySettings = preferences[DebuggerPreferenceKeys.REGISTRY].associate { registrySetting ->
            val parts = registrySetting.split("=")
            require(parts.size == 2) { "Registry options should have form registry=value" }
            parts[0] to parts[1]
        }.filter {
            Registry.get(it.key).asString() != it.value
        }
        val backup = registrySettings.keys.associateWith { Registry.get(it).asString() }
        for (r in registrySettings) {
            Registry.get(r.key).setValue(r.value)
        }
        atDebuggerTearDown {
            for (r in backup) {
                Registry.get(r.key).setValue(r.value)
            }
        }
    }

    private fun compileLibrariesAndTestSources(
        preferences: DebuggerPreferences,
        compilerFacility: DebuggerTestCompilerFacility
    ) {
        for (library in preferences[DebuggerPreferenceKeys.ATTACH_LIBRARY]) {
            if (library.startsWith("maven("))
                addMavenDependency(compilerFacility, library)
            else
                compilerFacility.compileExternalLibrary(library, librarySrcDirectory, libraryOutputDirectory)
        }

        compilerFacility.compileLibrary(librarySrcDirectory, libraryOutputDirectory)
        compileAdditionalLibraries(compilerFacility)
        compilerFacility.compileTestSourcesWithCli(
            myModule, jvmSourcesOutputDirectory, commonSourcesOutputDirectory, scriptSourcesOutputDirectory,
            File(appOutputPath), libraryOutputDirectory
        )
        sourcesKtFiles =
            compilerFacility.creatKtFiles(jvmSourcesOutputDirectory, commonSourcesOutputDirectory, scriptSourcesOutputDirectory)

        IndexingTestUtil.waitUntilIndexesAreReady(project)
    }

    // Provide a hook for subclasses to compile additional libraries.
    protected open fun compileAdditionalLibraries(compilerFacility: DebuggerTestCompilerFacility) {
    }

    protected open fun getMainClassName(compilerFacility: DebuggerTestCompilerFacility): String {
        if (pluginMode == KotlinPluginMode.K1) {
            // Although the implementation below is frontend-agnostic, K1 tests seem to depend on resolution ordering.
            // Some evaluation tests fail if not all files are analyzed at this point.
            return compilerFacility.analyzeAndFindMainClass(sourcesKtFiles.jvmKtFiles)
        }

        return runReadAction {
            val mainFunctionDetector = KotlinMainFunctionDetector.getInstance()
            val candidates = mutableListOf<ClassId>()

            for (file in sourcesKtFiles.jvmKtFiles) {
                val visitor = object : KtTreeVisitorVoid() {
                    override fun visitNamedFunction(function: KtNamedFunction) {
                        if (mainFunctionDetector.isMain(function)) {
                            val candidate = when (val containingClass = function.containingClassOrObject) {
                                null -> ClassId.topLevel(JvmFileClassUtil.getFileClassInfoNoResolve(file).facadeClassFqName)
                                else -> containingClass.classIdIfNonLocal
                            }

                            candidates.addIfNotNull(candidate)
                        }
                    }
                }

                file.accept(visitor)
            }

            when (candidates.size) {
                0 -> error("Cannot find a 'main()' function")
                1 -> {
                    val candidate = candidates.single()
                    val packagePrefix = if (candidate.packageFqName.isRoot) "" else candidate.packageFqName.asString() + "."
                    val relativeNameString = candidate.relativeClassName.asString().replace('.', '$')
                    packagePrefix + relativeNameString
                }
                else -> {
                    error("Multiple main functions found: " + candidates.joinToString())
                }
            }
        }
    }

    override fun createLocalProcess(className: String?) {
        LOG.assertTrue(myDebugProcess == null)
        myDebuggerSession = createLocalProcess(DebuggerSettings.SOCKET_TRANSPORT, createJavaParameters(className))
        myDebugProcess = myDebuggerSession.process
    }

    override fun createLocalProcess(transport: Int, javaParameters: JavaParameters): DebuggerSession? {
        createBreakpoints(javaParameters.mainClass)
        DebuggerSettings.getInstance().transport = transport

        val debuggerRunnerSettings = GenericDebuggerRunnerSettings()
        debuggerRunnerSettings.setLocal(true)
        debuggerRunnerSettings.transport = transport
        debuggerRunnerSettings.debugPort = if (transport == DebuggerSettings.SOCKET_TRANSPORT) "0" else DEFAULT_ADDRESS.toString()

        val environment = ExecutionEnvironmentBuilder(myProject, DefaultDebugExecutor.getDebugExecutorInstance())
            .runnerSettings(debuggerRunnerSettings)
            .runProfile(MockConfiguration(myProject, module))
            .build()

        environment.putUserData(DefaultDebugEnvironment.DEBUGGER_TRACE_MODE, traceMode)
        val debuggerSession = vmAttacher.attachVirtualMachine(this, javaParameters, environment)

        val processHandler = debuggerSession.process.processHandler
        debuggerSession.process.addProcessListener(object : ProcessListener {
            private val errorOutput = StringBuilder()

            override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                if (outputType == ProcessOutputTypes.STDERR) {
                    errorOutput.append(event.text)
                }
            }

            override fun startNotified(event: ProcessEvent) {
                print("Run Java\n", ProcessOutputTypes.SYSTEM)
                print("Connected to the target VM\n", ProcessOutputTypes.SYSTEM)
            }

            override fun processTerminated(event: ProcessEvent) {
                print("Disconnected from the target VM\n\n", ProcessOutputTypes.SYSTEM)
                print("Process finished with exit code ${event.exitCode}\n", ProcessOutputTypes.SYSTEM)

                if (event.exitCode != 0) {
                    print(errorOutput.toString(), ProcessOutputTypes.STDERR)
                }
            }
        })

        val process = DebuggerManagerEx.getInstanceEx(myProject).getDebugProcess(processHandler) as DebugProcessImpl
        assertNotNull(process)

        return debuggerSession
    }

    open fun addMavenDependency(compilerFacility: DebuggerTestCompilerFacility, library: String) {
    }

    abstract fun doMultiFileTest(files: TestFiles, preferences: DebuggerPreferences)

    override fun initOutputChecker(): OutputChecker {
        return KotlinOutputChecker(getTestDataPath(), testAppPath, appOutputPath, getExpectedOutputFile())
    }

    override fun setUpModule() {
        super.setUpModule()
        attachLibraries()
    }

    override fun setUpProject() {
        LocalFileSystem.getInstance().refreshAndFindFileByIoFile(File(appDataPath))
        super.setUpProject()
        File(appOutputPath).mkdirs()
    }

    override fun createBreakpoints(file: PsiFile?) {
        if (file != null) {
            val breakpointCreator = this.breakpointCreator ?: error(BreakpointCreator::class.java.simpleName + " should be set")
            breakpointCreator.createBreakpoints(file)
        }
    }

    override fun createJavaParameters(mainClass: String?): JavaParameters {
        return super.createJavaParameters(mainClass).apply {
            ModuleRootManager.getInstance(myModule).orderEntries.asSequence().filterIsInstance<LibraryOrderEntry>()
            classPath.add(TestKotlinArtifacts.kotlinStdlib)
            classPath.add(libraryOutputDirectory)
        }
    }

    private fun attachLibraries() {
        runWriteAction {
            val model = ModuleRootManager.getInstance(myModule).modifiableModel

            try {
                attachLibrary(
                  model, KOTLIN_LIBRARY_NAME,
                  listOf(TestKotlinArtifacts.kotlinStdlib, TestKotlinArtifacts.jetbrainsAnnotations),
                  listOf(TestKotlinArtifacts.kotlinStdlibSources, TestKotlinArtifacts.kotlinStdlibCommonSources)
                )

                attachLibrary(model, TEST_LIBRARY_NAME, listOf(libraryOutputDirectory), listOf(librarySrcDirectory))
            }
            finally {
                model.commit()
            }
        }
    }

    private fun updateIdeCompilerSettingsForEvaluator(
        languageVersion: LanguageVersion?,
        enabledLanguageFeatures: List<LanguageFeature>,
        compilerPlugins: List<String>,
    ) {
        if (languageVersion != null) {
            KotlinCommonCompilerArgumentsHolder.getInstance(project).update {
                this.languageVersion = languageVersion.versionString
            }
        }
        KotlinCommonCompilerArgumentsHolder.getInstance(project).update {
            this.pluginClasspaths = compilerPlugins.toTypedArray()
        }
        KotlinCompilerSettings.getInstance(project).update {
            this.additionalArguments = enabledLanguageFeatures.joinToString(" ") { "-XXLanguage:+${it.name}" }
        }
    }

    private fun restoreIdeCompilerSettings() = runInEdt {
        KotlinCommonCompilerArgumentsHolder.getInstance(project).update {
            this.languageVersion = KotlinPluginLayout.standaloneCompilerVersion.languageVersion.versionString
        }
        KotlinCompilerSettings.getInstance(project).update {
            this.additionalArguments = CompilerSettings.DEFAULT_ADDITIONAL_ARGUMENTS
        }
    }

    private fun detachLibraries() {
        runInEdtAndGet {
            ConfigLibraryUtil.removeLibrary(module, KOTLIN_LIBRARY_NAME)
            ConfigLibraryUtil.removeLibrary(module, TEST_LIBRARY_NAME)
        }
    }

    private fun attachLibrary(model: ModifiableRootModel, libraryName: String, classes: List<File>, sources: List<File>) {
        ConfigLibraryUtil.addLibrary(model, libraryName) {
            classes.forEach { addRoot(it, OrderRootType.CLASSES) }
            sources.forEach { addRoot(it, OrderRootType.SOURCES) }
        }
    }

    override fun runTestRunnable(testRunnable: ThrowableRunnable<Throwable>) {
        try {
            super.runTestRunnable(testRunnable)
        } catch (e: Throwable) {
            if (ignoreIsPassedCallback == null) {
                throw e
            }
            else {
                return
            }
        }
        ignoreIsPassedCallback?.invoke()
    }

    override fun checkTestOutput() {
        try {
            super.checkTestOutput()
        } catch (e: ComparisonFailure) {
            assertEqualsToFile(getExpectedOutputFile(), e.actual)
        }
    }

    protected fun getExpectedOutputFile(): File {
        val extensions = sequenceOf(
            ".scopes.out".takeIf { useInlineScopes },
            ".k2.out".takeIf { compileWithK2 },
            ".indy.out".takeIf { lambdasGenerationScheme() == JvmClosureGenerationScheme.INDY },
            ".out",
        )
        return extensions.filterNotNull()
            .map { File(getTestDataPath(), getTestName(true) + it) }
            .run { firstOrNull(File::exists) ?: last() }
    }

    override fun getData(dataId: String): Any? {
        if (XDebugSession.DATA_KEY.`is`(dataId)) {
            return myDebuggerSession?.xDebugSession
        }

        return super.getData(dataId)
    }
}

internal fun createTestFiles(wholeFile: File, wholeFileContents: String): TestFiles {
    val testFiles = createTestFiles(
        wholeFile.name,
        wholeFileContents,
        object : TestFileFactory<DebuggerTestModule, TestFileWithModule> {
            override fun createFile(
                module: DebuggerTestModule?,
                fileName: String,
                text: String,
                directives: Directives
            ): TestFileWithModule {
                return TestFileWithModule(module ?: DebuggerTestModule.Jvm.Default, fileName, text, directives)
            }

            override fun createModule(
                name: String,
                dependencies: MutableList<String>,
                friends: MutableList<String>
            ) =
                when {
                    name.startsWith(JVM_MODULE_NAME_START) -> DebuggerTestModule.Jvm(name, dependencies)
                    else -> DebuggerTestModule.Common(name, dependencies)
                }
        }
    )

    val wholeTestFile = TestFile(wholeFile.name, wholeFileContents)
    return TestFiles(wholeFile, wholeTestFile, testFiles)
}

class TestFiles(val originalFile: File, val wholeFile: TestFile, files: List<TestFileWithModule>) : List<TestFileWithModule> by files

sealed class DebuggerTestModule(name: String, dependencies: List<String>) : KotlinBaseTest.TestModule(name, dependencies, emptyList())  {
    class Common(name: String, dependencies: List<String>) : DebuggerTestModule(name, dependencies)
    class Jvm(name: String, dependencies: List<String>) : DebuggerTestModule(name, dependencies) {
        companion object {
            val Default = Jvm(JVM_MODULE_NAME_START, dependencies = emptyList())
        }
    }
}

class TestFileWithModule(
    val module: DebuggerTestModule,
    name: String,
    content: String,
    directives: Directives = Directives()
) : TestFile(name, content, directives)
