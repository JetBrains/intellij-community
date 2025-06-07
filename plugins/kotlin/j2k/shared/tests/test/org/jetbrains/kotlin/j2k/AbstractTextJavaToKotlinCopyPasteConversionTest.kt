// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.j2k

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.util.ThrowableRunnable
import org.jetbrains.kotlin.idea.AbstractCopyPasteTest
import org.jetbrains.kotlin.idea.base.test.IgnoreTests
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.editor.KotlinEditorOptions
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import org.jetbrains.kotlin.idea.test.runAll
import org.jetbrains.kotlin.idea.test.withCustomCompilerOptions
import org.jetbrains.kotlin.j2k.copyPaste.ConvertTextJavaCopyPasteProcessor
import java.awt.datatransfer.StringSelection
import java.io.File

abstract class AbstractTextJavaToKotlinCopyPasteConversionTest : AbstractCopyPasteTest() {
    private var oldEditorOptions: KotlinEditorOptions? = null

    override fun getProjectDescriptor(): KotlinWithJdkAndRuntimeLightProjectDescriptor = J2K_PROJECT_DESCRIPTOR

    override fun setUp() {
        super.setUp()
        oldEditorOptions = KotlinEditorOptions.getInstance().state
        KotlinEditorOptions.getInstance().isEnableJavaToKotlinConversion = true
        KotlinEditorOptions.getInstance().isDonTShowConversionDialog = true
    }

    override fun tearDown() {
        runAll(
            ThrowableRunnable { oldEditorOptions?.let { KotlinEditorOptions.getInstance().loadState(it) } },
            ThrowableRunnable { super.tearDown() }
        )
    }

    fun doTest(unused: String) {
        val testFile = dataFile()
        IgnoreTests.runTestIfNotDisabledByFileDirective(testFile.toPath(), getDisableTestDirective(pluginMode)) {
            withCustomCompilerOptions(testFile.readText(), project, module) {
                doTest(testFile)
            }
        }
    }

    private fun doTest(testFile: File) {
        val baseName = fileName().replace(".txt", "")

        myFixture.configureByFile("$baseName.txt")

        val fileText = dataFile().readText()
        val noConversionExpected = InTextDirectivesUtils.findListWithPrefixes(fileText, "// NO_CONVERSION_EXPECTED").isNotEmpty()

        // copy a file content directly to a buffer to keep it as is (keep original line endings etc)
        // TODO remove `getTextWithoutDirectives` call after we drop IGNORE_K2 directives in copy-paste tests (see KTIJ-15711)
        CopyPasteManager.getInstance().setContents(StringSelection(fileText.getTextWithoutDirectives()))

        configureByDependencyIfExists("$baseName.dependency.kt")
        configureByDependencyIfExists("$baseName.dependency.java")

        configureTargetFile("$baseName.to.kt")

        ConvertTextJavaCopyPasteProcessor.Util.conversionPerformed = false
        myFixture.performEditorAction(IdeActions.ACTION_PASTE)

        kotlin.test.assertEquals(
            noConversionExpected, !ConvertTextJavaCopyPasteProcessor.Util.conversionPerformed,
            if (noConversionExpected) "Conversion to Kotlin should not be suggested" else "No conversion to Kotlin suggested"
        )

        val expectedFile = getExpectedFile(testFile, isCopyPaste = true, pluginMode)
        val actualText = myFixture.file.text
        KotlinTestUtils.assertEqualsToFile(expectedFile, actualText)
    }
}
