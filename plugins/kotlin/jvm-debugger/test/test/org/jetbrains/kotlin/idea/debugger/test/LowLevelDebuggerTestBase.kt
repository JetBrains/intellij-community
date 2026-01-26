// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.debugger.test

import com.intellij.debugger.impl.OutputChecker
import com.intellij.execution.ExecutionTestCase
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.OrderRootType
import com.intellij.util.PathUtil
import com.intellij.util.SystemProperties
import com.intellij.util.ThrowableRunnable
import com.intellij.util.net.localhostInetAddress
import com.jetbrains.jdi.SocketListeningConnector
import com.sun.jdi.Location
import com.sun.jdi.ThreadReference
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.backend.common.output.OutputFile
import org.jetbrains.kotlin.config.JvmClosureGenerationScheme
import org.jetbrains.kotlin.config.JvmTarget
import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifacts
import org.jetbrains.kotlin.idea.debugger.core.FileApplicabilityChecker
import org.jetbrains.kotlin.idea.test.*
import org.jetbrains.kotlin.incremental.isClassFile
import org.jetbrains.kotlin.psi.KtFile
import java.io.File
import kotlin.properties.Delegates

abstract class LowLevelDebuggerTestBase : ExecutionTestCase(),
                                          ExpectedPluginModeProvider {

    private companion object {
        private const val CLASSES_DIRECTORY_NAME = "classes"
    }

    private lateinit var testAppDirectory: File
    private lateinit var jvmSourcesOutputDirectory: File
    private lateinit var commonSourcesOutputDirectory: File
    private lateinit var scriptSourcesOutputDirectory: File
    private lateinit var libraryOutputDirectory: File

    override fun getTestAppPath(): String = testAppDirectory.absolutePath

    override fun initOutputChecker(): OutputChecker = OutputChecker({ "" }, { "" })

    protected open val lambdasGenerationScheme: JvmClosureGenerationScheme get() = JvmClosureGenerationScheme.CLASS

    protected open val compileWithK2: Boolean get() = false

    override fun runBare(testRunnable: ThrowableRunnable<Throwable>) {
        testAppDirectory = KotlinTestUtils.tmpDir("debuggerTestSources")
        jvmSourcesOutputDirectory = File(testAppDirectory, SOURCES_DIRECTORY_NAME).apply { mkdirs() }
        commonSourcesOutputDirectory = File(testAppDirectory, COMMON_SOURCES_DIR).apply { mkdirs() }
        scriptSourcesOutputDirectory = File(testAppDirectory, SCRIPT_SOURCES_DIR).apply { mkdirs() }
        libraryOutputDirectory = File(testAppDirectory, "lib").apply { mkdirs() }
        super.runBare(testRunnable)
    }

    override fun setUp() {
        setUpWithKotlinPlugin { super.setUp() }
    }

    override fun compileProject() {
        // Do nothing.
        // skip com.intellij.execution.ExecutionTestCase.compileProject
        // as we are going to compile by org.jetbrains.kotlin.idea.debugger.test.LowLevelDebuggerTestBase.doTest(java.lang.String)
    }

    override fun setUpModule() {
        super.setUpModule()
        attachStdlib()
    }

    private fun attachStdlib() =
        runWriteAction {
            val model = ModuleRootManager.getInstance(myModule).modifiableModel
            try {
                ConfigLibraryUtil.addLibrary(model, KOTLIN_LIBRARY_NAME) {
                    addRoot(KotlinArtifacts.kotlinStdlib, OrderRootType.CLASSES)
                    addRoot(KotlinArtifacts.kotlinStdlibSources, OrderRootType.SOURCES)
                }
            } finally {
                model.commit()
            }
        }

    protected open fun createDebuggerTestCompilerFacility(
        testFiles: TestFiles, jvmTarget: JvmTarget, compileConfiguration: TestCompileConfiguration,
    ) = DebuggerTestCompilerFacility(project, testFiles, jvmTarget, compileConfiguration)

    fun doTest(testFilePath: String) {
        val wholeFile = File(testFilePath)
        val expectedText = KotlinTestUtils.doLoadFile(wholeFile)
        val testFiles = createTestFiles(wholeFile, expectedText)

        val options = parseOptions(wholeFile)
        val skipLoadingClasses = skipLoadingClasses(options)

        val classesDir = File(testAppDirectory, CLASSES_DIRECTORY_NAME)
        val compilerFacility = createDebuggerTestCompilerFacility(
            testFiles, JvmTarget.JVM_1_8,
            TestCompileConfiguration(
                lambdasGenerationScheme,
                languageVersion = chooseLanguageVersionForCompilation(compileWithK2),
                enabledLanguageFeatures = emptyList(),
                useInlineScopes = false,
                jvmDefaultMode = null,
            )
        )
        compilerFacility.compileTestSourcesWithCli(
            module, jvmSourcesOutputDirectory, commonSourcesOutputDirectory,
            scriptSourcesOutputDirectory, classesDir, libraryOutputDirectory
        )
        val sourceFiles =
            compilerFacility.creatKtFiles(jvmSourcesOutputDirectory, commonSourcesOutputDirectory, scriptSourcesOutputDirectory).jvmKtFiles


        val outputFiles = classesDir.walk()
            .filter { it.isClassFile() }
            .map { CompiledClassFile(it, relativePath = it.toRelativeString(classesDir)) }
            .toList()

        try {
            classesDir.apply {
                writeMainClass(this)
                for (classFile in outputFiles) {
                    File(this, classFile.relativePath).mkdirAndWriteBytes(classFile.asByteArray())
                }
            }

            val connector = SocketListeningConnector()
            val args = connector.defaultArguments().apply {
                getValue("localAddress").setValue(localhostInetAddress().hostAddress)
            }
            val port = connector.startListening(args).substringAfterLast(":")

            val process = startDebuggeeProcess(classesDir, skipLoadingClasses, outputFiles, port)

            val virtualMachine = connector.accept(args)

            try {
                val mainThread = virtualMachine.allThreads().single { it.name() == "main" }
                waitUntil { areCompiledClassesLoaded(mainThread, outputFiles, skipLoadingClasses) }
                val ranker: (List<KtFile>, Location) -> Map<KtFile, Int> = rankFiles(compilerFacility, sourceFiles, options)
                doTest(
                    options, mainThread, sourceFiles, jvmSourcesOutputDirectory, outputFiles, ranker
                )
            } finally {
                virtualMachine.exit(0)
                process.destroy()
            }
        } finally {
            classesDir.deleteRecursively()
        }
    }

    protected open fun rankFiles(
        compilerFacility: DebuggerTestCompilerFacility, sourceFiles: List<KtFile>, options: Set<String>
    ): (List<KtFile>, Location) -> Map<KtFile, Int> {
        return { files, location ->
            mapOf(runBlocking {
                FileApplicabilityChecker.chooseMostApplicableFile(files, location)
            } to 0)
        }
    }

    protected abstract fun doTest(
        options: Set<String>,
        mainThread: ThreadReference,
        sourceFiles: List<KtFile>,
        jvmSrcDir: File,
        outputFiles: List<CompiledClassFile>,
        ranker: (List<KtFile>, Location) -> Map<KtFile, Int>,
    )

    private fun areCompiledClassesLoaded(
        mainThread: ThreadReference,
        outputFiles: List<CompiledClassFile>,
        skipLoadingClasses: Set<String>
    ): Boolean {
        for (outputFile in outputFiles) {
            val fqName = outputFile.qualifiedName
            if (fqName in skipLoadingClasses) {
                continue
            }

            mainThread.virtualMachine().classesByName(fqName).firstOrNull() ?: return false
        }
        return true
    }

    protected open fun skipLoadingClasses(options: Set<String>): Set<String> {
        return emptySet()
    }

    private fun startDebuggeeProcess(classesDir: File,
                                     skipLoadingClasses: Set<String>,
                                     outputFiles: List<CompiledClassFile>,
                                     port: String
    ): Process {
        val classesToLoad = outputFiles
            .map { it.qualifiedName }
            .filter { it !in skipLoadingClasses }
            .joinToString(",")

        val classpath = listOf(
            classesDir.absolutePath,
            PathUtil.getJarPathForClass(Delegates::class.java) // Add Kotlin runtime JAR
        )

        val command = arrayOf(
            findJavaExecutable().absolutePath,
            "-agentlib:jdwp=transport=dt_socket,server=n,suspend=n,address=$port",
            "-ea",
            "-classpath", classpath.joinToString(File.pathSeparator),
            "-D${DebuggerMain.CLASSES_TO_LOAD}=$classesToLoad",
            DebuggerMain::class.java.name
        )

        return ProcessBuilder(*command).inheritIO().start()
    }

    private fun findJavaExecutable(): File {
        val javaBin = File(SystemProperties.getJavaHome(), "bin")
        return File(javaBin, "java.exe").takeIf { it.exists() }
            ?: File(javaBin, "java").also { assert(it.exists()) }
    }

    private fun writeMainClass(classesDir: File) {
        val mainClassResourceName = DebuggerMain::class.java.name.replace('.', '/') + ".class"
        val resource = javaClass.classLoader.getResource(mainClassResourceName)
            ?: error("Resource not found: $mainClassResourceName")

        val mainClassBytes = resource.readBytes()
        File(classesDir, mainClassResourceName).mkdirAndWriteBytes(mainClassBytes)
    }

    internal val OutputFile.internalName
        get() = relativePath.substringBeforeLast(".class")

    private val OutputFile.qualifiedName
        get() = internalName.replace('/', '.')

    class CompiledClassFile(private val file: File, val relativePath: String) {
        private val internalName: String
            get() = relativePath.substringBefore(".class")

        val qualifiedName: String
            get() = internalName.replace("/", ".")
        fun asByteArray(): ByteArray = file.readBytes()
    }
}

private fun File.mkdirAndWriteBytes(array: ByteArray) {
    parentFile.mkdirs()
    writeBytes(array)
}

private fun waitUntil(condition: () -> Boolean) {
    while (!condition()) {
        Thread.sleep(30)
    }
}

private object DebuggerMain {
    const val CLASSES_TO_LOAD = "classes.to.load"

    @JvmField
    val lock = Any()

    @JvmStatic
    fun main(args: Array<String>) {
        System.getProperty(CLASSES_TO_LOAD).split(',').forEach { Class.forName(it) }
        synchronized(lock) {
            // Wait until debugger is attached
            @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
            (lock as Object).wait()
        }
    }
}

private fun parseOptions(file: File): Set<String> =
    file.readLines()
        .asSequence()
        .filter { it.matches("^// ?[\\w_]+(:.*)?$".toRegex()) }
        .map { it.drop(2).trim() }
        .filter { !it.startsWith("FILE:") }
        .toSet()
