// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.test

import com.google.common.collect.Lists
import com.intellij.debugger.PositionManager
import com.intellij.debugger.SourcePosition
import com.intellij.debugger.engine.DebugProcess
import com.intellij.debugger.engine.DebugProcessEvents
import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.events.DebuggerCommandImpl
import com.intellij.debugger.jdi.VirtualMachineProxyImpl
import com.intellij.openapi.application.readAction
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.RunAll.Companion.runAll
import com.sun.jdi.Location
import com.sun.jdi.ReferenceType
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.backend.common.output.OutputFileCollection
import org.jetbrains.kotlin.codegen.ClassBuilderFactories
import org.jetbrains.kotlin.codegen.GenerationUtils
import org.jetbrains.kotlin.codegen.state.GenerationState
import org.jetbrains.kotlin.config.*
import org.jetbrains.kotlin.config.JvmAnalysisFlags.suppressMissingBuiltinsError
import org.jetbrains.kotlin.idea.checkers.CompilerTestLanguageVersionSettings
import org.jetbrains.kotlin.idea.debugger.KotlinPositionManager
import org.jetbrains.kotlin.idea.debugger.core.KotlinPositionManagerFactory
import org.jetbrains.kotlin.idea.debugger.test.mock.MockLocation
import org.jetbrains.kotlin.idea.debugger.test.mock.MockVirtualMachine
import org.jetbrains.kotlin.idea.debugger.test.mock.SmartMockReferenceTypeContext
import org.jetbrains.kotlin.idea.test.*
import org.jetbrains.kotlin.load.kotlin.PackagePartProvider
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.test.TestJdkKind
import java.io.File
import java.io.IOException
import java.util.*
import java.util.regex.Pattern

abstract class AbstractPositionManagerTest : KotlinLightCodeInsightFixtureTestCase() {
    public override fun setUp() {
        super.setUp()
        myFixture.testDataPath = DEBUGGER_TESTDATA_PATH_BASE
    }

    private var debugProcess: DebugProcessImpl? = null

    override fun getProjectDescriptor(): LightProjectDescriptor {
        return KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()
    }

    protected fun doTest(fileName: String) {
        val path = getPath(fileName)

        if (fileName.endsWith(".kt")) {
            myFixture.configureByFile(path)
        } else {
            File(path).walkTopDown().forEach { file: File ->
                val fileName1 = file.name
                val path1 = getPath(fileName1)
                myFixture.configureByFile(path1)
            }
        }

        performTest()
    }

    private fun performTest() {
        val project = project
        val files: List<KtFile> = ArrayList(project.allKotlinFiles())
        if (files.isEmpty()) return

        val breakpoints: MutableList<Breakpoint> = Lists.newArrayList()
        for (file in files) {
            breakpoints.addAll(extractBreakpointsInfo(file, file.text))
        }

        val configuration = KotlinTestUtils.newConfiguration(ConfigurationKind.STDLIB, TestJdkKind.MOCK_JDK)
        // TODO: delete this once IDEVirtualFileFinder supports loading .kotlin_builtins files
        configuration.languageVersionSettings = CompilerTestLanguageVersionSettings(
            emptyMap(),
            ApiVersion.LATEST_STABLE,
            LanguageVersion.LATEST_STABLE,
            Collections.singletonMap(suppressMissingBuiltinsError, true)
        )

        val state = getCompileFiles(files, configuration)

        val referencesByName = getReferenceMap(state.factory)

        debugProcess = createDebugProcess(referencesByName)

        val positionManager: PositionManager = createPositionManager(debugProcess!!)

        runBlocking {
            for (breakpoint in breakpoints) {
                assertBreakpointIsHandledCorrectly(breakpoint, positionManager)
            }
        }

    }

    protected open fun getCompileFiles(files: List<KtFile>, configuration: CompilerConfiguration): GenerationState {
        return GenerationUtils.compileFiles(files, configuration, ClassBuilderFactories.TEST, { PackagePartProvider.Empty }).first
    }

    public override fun tearDown() {
        runAll({
                   debugProcess?.apply {
                       stop(true)
                       waitFor()
                   }

               }, {
                   if (debugProcess != null) {
                       debugProcess!!.dispose()
                       debugProcess = null
                   }
               },
               { super.tearDown() }
        )
    }

    private fun createDebugProcess(referencesByName: Map<String, ReferenceType>): DebugProcessEvents {
        return object : DebugProcessEvents(project) {
            private var virtualMachineProxy: VirtualMachineProxyImpl? = null

            override fun getVirtualMachineProxy(): VirtualMachineProxyImpl {
                if (virtualMachineProxy == null) {
                    virtualMachineProxy = MockVirtualMachineProxy(this, referencesByName)
                }
                return virtualMachineProxy!!
            }

            override fun getSearchScope(): GlobalSearchScope {
                return GlobalSearchScope.allScope(project)
            }
        }
    }

    private class Breakpoint(
        val file: KtFile, // 0-based
        val lineNumber: Int, val classNameRegexp: String
    )

    private class MockVirtualMachineProxy(
        debugProcess: DebugProcessEvents?,
        private val referencesByName: Map<String, ReferenceType>
    ) : VirtualMachineProxyImpl(
        debugProcess,
        MockVirtualMachine()
    ) {
        override fun allClasses(): List<ReferenceType> {
            return ArrayList(referencesByName.values)
        }

        override fun classesByName(name: String): List<ReferenceType> {
            return listOfNotNull(referencesByName[name])
        }
    }

    private suspend fun assertBreakpointIsHandledCorrectly(breakpoint: Breakpoint, positionManager: PositionManager) {
        val position = readAction { SourcePosition.createFromLine(breakpoint.file, breakpoint.lineNumber)  }
        val classes = positionManager.getAllClasses(position)
        assertNotNull(classes)
        assertFalse(
            "Classes not found for line " + (breakpoint.lineNumber + 1) + ", expected " + breakpoint.classNameRegexp,
            classes.isEmpty()
        )

        if (classes.none { clazz: ReferenceType -> clazz.name().matches(breakpoint.classNameRegexp.toRegex()) }) {
            throw AssertionError("Breakpoint class '" + breakpoint.classNameRegexp +
                                         "' from line " + (breakpoint.lineNumber + 1) + " was not found in the PositionManager classes names: " +
                                         classes.joinToString(",") { it.name() }
            )
        }

        val typeWithFqName = classes[0]
        val location: Location = MockLocation(typeWithFqName, breakpoint.file.name, breakpoint.lineNumber + 1)

        var actualPosition: SourcePosition? = null
        debugProcess!!.managerThread.invokeAndWait(object : DebuggerCommandImpl() {
            override fun action() {
                actualPosition = positionManager.getSourcePosition(location)
            }
        })

        assertNotNull(actualPosition)
        assertEquals(position.file, actualPosition!!.file)
        assertEquals(position.line, actualPosition!!.line)
    }

    companion object {
        // Breakpoint is given as a line comment on a specific line, containing the regexp to match the name of the class where that line
        // can be found. This pattern matches against these line comments and saves the class name in the first group
        private val BREAKPOINT_PATTERN: Pattern = Pattern.compile("^.*//\\s*(.+)\\s*$")

        private fun createPositionManager(process: DebugProcess): KotlinPositionManager {
            val positionManager = KotlinPositionManagerFactory().createPositionManager(process) as KotlinPositionManager?
            assertNotNull(positionManager)
            return positionManager!!
        }

        private fun getPath(fileName: String): String {
            val path: String
            try {
                path = File(fileName).canonicalPath
            } catch (e: IOException) {
                throw RuntimeException(e)
            }
            return path.substringAfter(DEBUGGER_TESTDATA_PATH_BASE, path)
        }

        private fun extractBreakpointsInfo(file: KtFile, fileContent: String): Collection<Breakpoint> {
            val breakpoints: MutableCollection<Breakpoint> = Lists.newArrayList()
            val lines = StringUtil.convertLineSeparators(fileContent).split("\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

            for (i in lines.indices) {
                val matcher = BREAKPOINT_PATTERN.matcher(lines[i])
                if (matcher.matches()) {
                    breakpoints.add(Breakpoint(file, i, matcher.group(1)))
                }
            }

            return breakpoints
        }

        private fun getReferenceMap(outputFiles: OutputFileCollection): Map<String, ReferenceType> {
            return SmartMockReferenceTypeContext(outputFiles).referenceTypesByName
        }
    }
}
