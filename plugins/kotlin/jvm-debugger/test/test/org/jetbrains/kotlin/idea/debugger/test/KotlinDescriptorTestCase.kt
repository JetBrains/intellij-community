// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.debugger.test

import com.intellij.debugger.DebuggerManagerEx
import com.intellij.debugger.DefaultDebugEnvironment
import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.impl.*
import com.intellij.debugger.settings.DebuggerSettings
import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.JavaCommandLineState
import com.intellij.execution.configurations.JavaParameters
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.execution.target.TargetEnvironmentRequest
import com.intellij.execution.target.TargetedCommandLineBuilder
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiFile
import com.intellij.testFramework.EdtTestUtil
import com.intellij.util.ThrowableRunnable
import com.intellij.util.ui.UIUtil
import com.intellij.xdebugger.XDebugSession
import org.jetbrains.kotlin.config.JvmTarget
import org.jetbrains.kotlin.idea.artifacts.KotlinArtifacts
import org.jetbrains.kotlin.idea.debugger.evaluate.KotlinDebuggerCaches
import org.jetbrains.kotlin.idea.debugger.test.preference.*
import org.jetbrains.kotlin.idea.debugger.test.util.BreakpointCreator
import org.jetbrains.kotlin.idea.debugger.test.util.KotlinOutputChecker
import org.jetbrains.kotlin.idea.debugger.test.util.LogPropagator
import org.jetbrains.kotlin.idea.test.ConfigLibraryUtil
import org.jetbrains.kotlin.idea.test.PluginTestCaseBase
import org.jetbrains.kotlin.idea.test.addRoot
import org.jetbrains.kotlin.idea.test.runAll
import org.jetbrains.kotlin.test.Directives
import org.jetbrains.kotlin.test.KotlinBaseTest.TestFile
import org.jetbrains.kotlin.test.KotlinTestUtils
import org.jetbrains.kotlin.test.KotlinTestUtils.*
import org.junit.ComparisonFailure
import java.io.File

internal const val KOTLIN_LIBRARY_NAME = "KotlinLibrary"
internal const val TEST_LIBRARY_NAME = "TestLibrary"

class TestFiles(val originalFile: File, val wholeFile: TestFile, files: List<TestFile>) : List<TestFile> by files

abstract class KotlinDescriptorTestCase : DescriptorTestCase() {
    private lateinit var testAppDirectory: File
    private lateinit var sourcesOutputDirectory: File

    private lateinit var librarySrcDirectory: File
    private lateinit var libraryOutputDirectory: File

    private lateinit var mainClassName: String

    override fun getTestAppPath(): String = testAppDirectory.absolutePath
    override fun getTestProjectJdk() = PluginTestCaseBase.fullJdk()

    private fun systemLogger(message: String) = println(message, ProcessOutputTypes.SYSTEM)

    private var breakpointCreator: BreakpointCreator? = null
    private var logPropagator: LogPropagator? = null

    private var oldValues: OldValuesStorage? = null

    override fun runBare(testRunnable: ThrowableRunnable<Throwable>) {
        testAppDirectory = tmpDir("debuggerTestSources")
        sourcesOutputDirectory = File(testAppDirectory, "src").apply { mkdirs() }

        librarySrcDirectory = File(testAppDirectory, "libSrc").apply { mkdirs() }
        libraryOutputDirectory = File(testAppDirectory, "lib").apply { mkdirs() }

        super.runBare(testRunnable)
    }

    override fun setUp() {
        super.setUp()

        KotlinDebuggerCaches.LOG_COMPILATIONS = true
        logPropagator = LogPropagator(::systemLogger).apply { attach() }
    }

    override fun tearDown() {
        runAll(
            ThrowableRunnable { KotlinDebuggerCaches.LOG_COMPILATIONS = false },
            ThrowableRunnable { oldValues?.revertValues() },
            ThrowableRunnable { oldValues = null },
            ThrowableRunnable { detachLibraries() },
            ThrowableRunnable { logPropagator?.detach() },
            ThrowableRunnable { logPropagator = null },
            ThrowableRunnable { super.tearDown() }
        )
    }

    protected fun testDataFile(fileName: String): File = File(getTestDataPath(), fileName)

    protected fun testDataFile(): File = testDataFile(fileName())

    protected open fun fileName(): String = getTestDataFileName(this::class.java, this.name) ?: (getTestName(false) + ".kt")

    fun getTestDataPath(): String = getTestsRoot(this::class.java)

    open fun useIrBackend() = false

    fun doTest(unused: String) {
        val wholeFile = testDataFile()
        val wholeFileContents = FileUtil.loadFile(wholeFile, true)

        val testFiles = createTestFiles(wholeFile, wholeFileContents)
        val preferences = DebuggerPreferences(myProject, wholeFileContents)

        oldValues = SettingsMutators.mutate(preferences)

        val rawJvmTarget = preferences[DebuggerPreferenceKeys.JVM_TARGET]
        val jvmTarget = JvmTarget.fromString(rawJvmTarget) ?: error("Invalid JVM target value: $rawJvmTarget")

        val compilerFacility = DebuggerTestCompilerFacility(testFiles, jvmTarget, useIrBackend())

        for (library in preferences[DebuggerPreferenceKeys.ATTACH_LIBRARY]) {
            if (library.startsWith("maven("))
                addMavenDependency(compilerFacility, library)
            else
                compilerFacility.compileExternalLibrary(library, librarySrcDirectory, libraryOutputDirectory)
        }

        compilerFacility.compileLibrary(librarySrcDirectory, libraryOutputDirectory)
        mainClassName = compilerFacility.compileTestSources(myModule, sourcesOutputDirectory, File(appOutputPath), libraryOutputDirectory)

        breakpointCreator = BreakpointCreator(
            project,
            ::systemLogger,
            preferences
        ).apply { createAdditionalBreakpoints(wholeFileContents) }

        createLocalProcess(mainClassName)
        doMultiFileTest(testFiles, preferences)
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
            .runProfile(object : MockConfiguration() {
                override fun getProject() = myProject
            })
            .build()

        val javaCommandLineState: JavaCommandLineState = object : JavaCommandLineState(environment) {
            override fun createJavaParameters() = javaParameters

            override fun createTargetedCommandLine(request: TargetEnvironmentRequest): TargetedCommandLineBuilder {
                return getJavaParameters().toCommandLine(request)
            }
        }

        val debugParameters =
            RemoteConnectionBuilder(
                debuggerRunnerSettings.LOCAL,
                debuggerRunnerSettings.getTransport(),
                debuggerRunnerSettings.getDebugPort()
            )
                .checkValidity(true)
                .asyncAgent(true)
                .create(javaCommandLineState.javaParameters)

        lateinit var debuggerSession: DebuggerSession

        UIUtil.invokeAndWaitIfNeeded(Runnable {
            try {
                val env = javaCommandLineState.environment
                env.putUserData(DefaultDebugEnvironment.DEBUGGER_TRACE_MODE, traceMode)
                debuggerSession = attachVirtualMachine(javaCommandLineState, env, debugParameters, false)
            } catch (e: ExecutionException) {
                fail(e.message)
            }
        })

        val processHandler = debuggerSession.process.processHandler
        debuggerSession.process.addProcessListener(object : ProcessAdapter() {
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

    private fun createTestFiles(wholeFile: File, wholeFileContents: String): TestFiles {
        val testFiles = org.jetbrains.kotlin.test.TestFiles.createTestFiles(
            wholeFile.name,
            wholeFileContents,
            object : org.jetbrains.kotlin.test.TestFiles.TestFileFactoryNoModules<TestFile>() {
                override fun create(fileName: String, text: String, directives: Directives): TestFile {
                    return TestFile(fileName, text, directives)
                }
            }
        )

        val wholeTestFile = TestFile(wholeFile.name, wholeFileContents)
        return TestFiles(wholeFile, wholeTestFile, testFiles)
    }

    abstract fun doMultiFileTest(files: TestFiles, preferences: DebuggerPreferences)

    override fun initOutputChecker(): OutputChecker {
        return KotlinOutputChecker(getTestDataPath(), testAppPath, appOutputPath, useIrBackend(), getExpectedOutputFile())
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
            classPath.add(KotlinArtifacts.instance.kotlinStdlib)
            classPath.add(libraryOutputDirectory)
        }
    }

    private fun attachLibraries() {
        runWriteAction {
            val model = ModuleRootManager.getInstance(myModule).modifiableModel

            try {
                attachLibrary(
                  model, KOTLIN_LIBRARY_NAME,
                  listOf(KotlinArtifacts.instance.kotlinStdlib, KotlinArtifacts.instance.jetbrainsAnnotations),
                  listOf(KotlinArtifacts.instance.kotlinStdlibSources)
                )

                attachLibrary(model, TEST_LIBRARY_NAME, listOf(libraryOutputDirectory), listOf(librarySrcDirectory))
            }
            finally {
                model.commit()
            }
        }
    }

    private fun detachLibraries() {
        EdtTestUtil.runInEdtAndGet(ThrowableComputable {
            ConfigLibraryUtil.removeLibrary(module, KOTLIN_LIBRARY_NAME)
            ConfigLibraryUtil.removeLibrary(module, TEST_LIBRARY_NAME)
        })
    }

    private fun attachLibrary(model: ModifiableRootModel, libraryName: String, classes: List<File>, sources: List<File>) {
        ConfigLibraryUtil.addLibrary(model, libraryName) {
            classes.forEach { addRoot(it, OrderRootType.CLASSES) }
            sources.forEach { addRoot(it, OrderRootType.SOURCES) }
        }
    }

    override fun checkTestOutput() {
        try {
            super.checkTestOutput()
        } catch (e: ComparisonFailure) {
            KotlinTestUtils.assertEqualsToFile(getExpectedOutputFile(), e.actual)
        }
    }

    protected fun getExpectedOutputFile(): File {
        if (useIrBackend()) {
            val irOut = File(getTestDataPath(), getTestName(true) + ".ir.out")
            if (irOut.exists()) return irOut
        }
        return File(getTestDataPath(), getTestName(true) + ".out")
    }

    override fun getData(dataId: String): Any? {
        if (XDebugSession.DATA_KEY.`is`(dataId)) {
            return myDebuggerSession?.xDebugSession
        }

        return super.getData(dataId)
    }
}
