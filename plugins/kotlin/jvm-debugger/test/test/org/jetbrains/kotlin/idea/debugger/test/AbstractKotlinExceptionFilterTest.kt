// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.debugger.test

import com.intellij.execution.filters.FileHyperlinkInfo
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.ThrowableRunnable
import org.jetbrains.kotlin.idea.codegen.forTestCompile.ForTestCompileRuntime
import org.jetbrains.kotlin.idea.core.util.toVirtualFile
import org.jetbrains.kotlin.idea.debugger.core.InlineFunctionHyperLinkInfo
import org.jetbrains.kotlin.idea.debugger.core.KotlinExceptionFilterFactory
import org.jetbrains.kotlin.idea.test.*
import java.io.File
import java.lang.reflect.InvocationTargetException
import java.net.URLClassLoader

abstract class AbstractKotlinExceptionFilterTest : KotlinLightCodeInsightFixtureTestCase() {
    private companion object {
        val MOCK_LIBRARY_SOURCES = IDEA_TEST_DATA_DIR.resolve("debugger/mockLibraryForExceptionFilter")
    }

    private lateinit var mockLibraryFacility: MockLibraryFacility

    override fun fileName(): String {
        val testName = getTestName(true)
        return "$testName/$testName.kt"
    }

    override fun setUp() {
        super.setUp()

        val mainFile = dataFile()
        val testDir = mainFile.parentFile ?: error("Invalid test directory")

        val fileText = FileUtil.loadFile(mainFile, true)
        val extraOptions = InTextDirectivesUtils.findListWithPrefixes(fileText, "// !LANGUAGE: ").map { "-XXLanguage:$it" }

        mockLibraryFacility = MockLibraryFacility(
            sources = listOf(MOCK_LIBRARY_SOURCES, testDir),
            options = extraOptions
        )

        mockLibraryFacility.setUp(module)
    }

    override fun tearDown() {
        runAll(
            ThrowableRunnable { mockLibraryFacility.tearDown(module) },
            ThrowableRunnable { super.tearDown() }
        )
    }

    protected fun doTest(unused: String) {
        val mainFile = dataFile()
        val testDir = mainFile.parentFile ?: error("Invalid test directory")

        val fileText = FileUtil.loadFile(mainFile, true)

        val mainClass = InTextDirectivesUtils.stringWithDirective(fileText, "MAIN_CLASS")
        val prefix = InTextDirectivesUtils.findStringWithPrefixes(fileText, "// PREFIX: ") ?: "at"

        val stackTraceElement = getStackTraceElement(mainClass)
        val stackTraceString = stackTraceElement.toString()
        val text = "$prefix $stackTraceString"

        val filter = KotlinExceptionFilterFactory().create(GlobalSearchScope.allScope(project))
        var result = filter.applyFilter(text, text.length) ?: throw AssertionError("Couldn't apply filter to $stackTraceElement")

        if (InTextDirectivesUtils.isDirectiveDefined(fileText, "SMAP_APPLIED")) {
            val info = result.firstHyperlinkInfo as FileHyperlinkInfo
            val descriptor = info.descriptor!!

            val file = descriptor.file
            val line = descriptor.line + 1

            val newStackString = stackTraceString
                .replace(mainFile.name, file.name)
                .replace(Regex(":\\d+\\)"), ":$line)")

            val newText = "$prefix $newStackString"
            result = filter.applyFilter(newText, newText.length) ?: throw AssertionError("Couldn't apply filter to $stackTraceElement")
        }

        val info = result.firstHyperlinkInfo as FileHyperlinkInfo

        val descriptor = if (InTextDirectivesUtils.isDirectiveDefined(fileText, "NAVIGATE_TO_CALL_SITE")) {
          (info as? InlineFunctionHyperLinkInfo)?.callSiteDescriptor
        } else {
            info.descriptor
        }

        if (descriptor == null) {
            throw AssertionError("`$stackTraceString` did not resolve to an inline function call")
        }

        val expectedFileName = InTextDirectivesUtils.findStringWithPrefixes(fileText, "// FILE: ")!!
        val expectedVirtualFile = File(testDir, expectedFileName).toVirtualFile()
            ?: File(MOCK_LIBRARY_SOURCES, expectedFileName).toVirtualFile()
            ?: throw AssertionError("Couldn't find file: name = $expectedFileName")
        val expectedLineNumber = InTextDirectivesUtils.getPrefixedInt(fileText, "// LINE: ") ?: error("Line directive not found")

        val document = FileDocumentManager.getInstance().getDocument(expectedVirtualFile)!!
        val expectedOffset = document.getLineStartOffset(expectedLineNumber - 1)

        val expectedLocation = "$expectedFileName:$expectedOffset"
        val actualLocation = descriptor.file.name + ":" + descriptor.offset
        assertEquals("Wrong result for line $stackTraceElement", expectedLocation, actualLocation)
    }

    private fun getStackTraceElement(mainClass: String): StackTraceElement {
        val libraryRoots = ModuleRootManager.getInstance(module).orderEntries().runtimeOnly().withoutSdk()
            .classesRoots.map { VfsUtilCore.virtualToIoFile(it).toURI().toURL() }

        val classLoader = URLClassLoader(libraryRoots.toTypedArray(), ForTestCompileRuntime.runtimeJarClassLoader())

        return try {
            val clazz = classLoader.loadClass(mainClass)
            clazz.getMethod("box").invoke(null)
            throw AssertionError("class $mainClass should have box() method and throw exception")
        } catch (e: InvocationTargetException) {
            e.targetException.stackTrace[0]
        }
    }
}
