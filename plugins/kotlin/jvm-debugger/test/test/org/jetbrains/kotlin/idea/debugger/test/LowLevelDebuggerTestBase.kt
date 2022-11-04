// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.debugger.test

import com.intellij.debugger.impl.OutputChecker
import com.intellij.execution.ExecutionTestCase
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.OrderRootType
import com.intellij.util.PathUtil
import com.intellij.util.SystemProperties
import com.intellij.util.ThrowableRunnable
import com.jetbrains.jdi.SocketAttachingConnector
import com.sun.jdi.ThreadReference
import com.sun.jdi.VirtualMachine
import org.jetbrains.kotlin.backend.common.output.OutputFile
import org.jetbrains.kotlin.codegen.*
import org.jetbrains.kotlin.codegen.state.GenerationState
import org.jetbrains.kotlin.config.JvmTarget
import org.jetbrains.kotlin.idea.base.plugin.artifacts.KotlinArtifacts
import org.jetbrains.kotlin.idea.test.ConfigLibraryUtil
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.idea.test.addRoot
import com.intellij.openapi.application.runWriteAction
import java.io.File
import java.io.IOException
import java.net.Socket
import kotlin.properties.Delegates

abstract class LowLevelDebuggerTestBase : ExecutionTestCase() {
    private companion object {
        private const val CLASSES_DIRECTORY_NAME = "classes"
        private const val DEBUG_ADDRESS = "127.0.0.1"
        private const val DEBUG_PORT = 5115
    }

    private lateinit var classFileFactory: ClassFileFactory

    private lateinit var testAppDirectory: File
    private lateinit var jvmSourcesOutputDirectory: File

    override fun getTestAppPath(): String = testAppDirectory.absolutePath

    override fun initOutputChecker(): OutputChecker = OutputChecker({ "" }, { "" })

    override fun runBare(testRunnable: ThrowableRunnable<Throwable>) {
        testAppDirectory = KotlinTestUtils.tmpDir("debuggerTestSources")
        jvmSourcesOutputDirectory = File(testAppDirectory, SOURCES_DIRECTORY_NAME).apply { mkdirs() }
        super.runBare(testRunnable)
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

    protected open fun createDebuggerTestCompilerFacility(testFiles: TestFiles, jvmTarget: JvmTarget, useIrBackend: Boolean) =
        DebuggerTestCompilerFacility(testFiles, jvmTarget, useIrBackend)

    fun doTest(testFilePath: String) {
        val wholeFile = File(testFilePath)
        val expectedText = KotlinTestUtils.doLoadFile(wholeFile)
        val testFiles = createTestFiles(wholeFile, expectedText)

        val options = parseOptions(wholeFile)
        val skipLoadingClasses = skipLoadingClasses(options)

        val classesDir = File(testAppDirectory, CLASSES_DIRECTORY_NAME)
        val classBuilderFactory = OriginCollectingClassBuilderFactory(ClassBuilderMode.FULL)
        val compilerFacility = createDebuggerTestCompilerFacility(testFiles, JvmTarget.JVM_1_8, useIrBackend = true)
        val compilationResult = compilerFacility.compileTestSources(
            project, jvmSourcesOutputDirectory, classesDir, classBuilderFactory
        )
        val generationState = compilationResult.generationState
        classFileFactory = generationState.factory

        try {
            classesDir.apply {
                writeMainClass(this)
                for (classFile in classFileFactory.getClassFiles()) {
                    File(this, classFile.relativePath).mkdirAndWriteBytes(classFile.asByteArray())
                }
            }

            val process = startDebuggeeProcess(classesDir, skipLoadingClasses)
            waitUntil { isPortOpen() }

            val virtualMachine = attachDebugger()

            try {
                val mainThread = virtualMachine.allThreads().single { it.name() == "main" }
                waitUntil { areCompiledClassesLoaded(mainThread, classFileFactory, skipLoadingClasses) }
                doTest(options, mainThread, classBuilderFactory, generationState)
            } finally {
                virtualMachine.exit(0)
                process.destroy()
            }
        } finally {
            classesDir.deleteRecursively()
        }
    }

    protected abstract fun doTest(
        options: Set<String>,
        mainThread: ThreadReference,
        factory: OriginCollectingClassBuilderFactory,
        state: GenerationState
    )

    private fun isPortOpen(): Boolean {
        return try {
            Socket(DEBUG_ADDRESS, DEBUG_PORT).close()
            true
        } catch (e: IOException) {
            false
        }
    }

    private fun areCompiledClassesLoaded(
        mainThread: ThreadReference,
        classFileFactory: ClassFileFactory,
        skipLoadingClasses: Set<String>
    ): Boolean {
        for (outputFile in classFileFactory.getClassFiles()) {
            val fqName = outputFile.internalName.replace('/', '.')
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

    private fun startDebuggeeProcess(classesDir: File, skipLoadingClasses: Set<String>): Process {
        val classesToLoad = classFileFactory.getClassFiles()
            .map { it.qualifiedName }
            .filter { it !in skipLoadingClasses }
            .joinToString(",")

        val classpath = listOf(
            classesDir.absolutePath,
            PathUtil.getJarPathForClass(Delegates::class.java) // Add Kotlin runtime JAR
        )

        val command = arrayOf(
            findJavaExecutable().absolutePath,
            "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=$DEBUG_PORT",
            "-ea",
            "-classpath", classpath.joinToString(File.pathSeparator),
            "-D${DebuggerMain.CLASSES_TO_LOAD}=$classesToLoad",
            DebuggerMain::class.java.name
        )

        return ProcessBuilder(*command).inheritIO().start()
    }

    private fun attachDebugger(): VirtualMachine {
        val connector = SocketAttachingConnector()
        return connector.attach(connector.defaultArguments().toMutableMap().apply {
            getValue("port").setValue("$DEBUG_PORT")
            getValue("hostname").setValue(DEBUG_ADDRESS)
        })
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
