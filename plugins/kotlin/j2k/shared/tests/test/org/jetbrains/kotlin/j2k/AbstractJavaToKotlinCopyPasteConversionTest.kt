// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.j2k

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.util.ThrowableRunnable
import com.intellij.util.ui.UIUtil
import org.jetbrains.kotlin.idea.AbstractCopyPasteTest
import org.jetbrains.kotlin.idea.base.test.IgnoreTests
import org.jetbrains.kotlin.idea.base.test.InTextDirectivesUtils
import org.jetbrains.kotlin.idea.editor.KotlinEditorOptions
import org.jetbrains.kotlin.idea.test.*
import org.jetbrains.kotlin.j2k.copyPaste.ConvertJavaCopyPasteProcessor
import java.io.File
import kotlin.test.assertEquals

abstract class AbstractJavaToKotlinCopyPasteConversionTest : AbstractCopyPasteTest() {
    private var oldEditorOptions: KotlinEditorOptions? = null

    override val testDataDirectory: File
        get() = IDEA_TEST_DATA_DIR.resolve("copyPaste/conversion")

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
        val baseName = fileName().replace(".java", "")
        myFixture.configureByFiles("$baseName.java")

        val fileText = myFixture.editor.document.text
        val noConversionExpected = InTextDirectivesUtils.findListWithPrefixes(fileText, "// NO_CONVERSION_EXPECTED").isNotEmpty()

        myFixture.performEditorAction(IdeActions.ACTION_COPY)

        configureByDependencyIfExists("$baseName.dependency.kt")
        configureByDependencyIfExists("$baseName.dependency.java")

        configureTargetFile("$baseName.to.kt")

            ConvertJavaCopyPasteProcessor.Util.conversionPerformed = false

            myFixture.performEditorAction(IdeActions.ACTION_PASTE)
            UIUtil.dispatchAllInvocationEvents()

        assertEquals(
            noConversionExpected, !ConvertJavaCopyPasteProcessor.Util.conversionPerformed,
            if (noConversionExpected) "Conversion to Kotlin should not be suggested" else "No conversion to Kotlin suggested"
        )

        val expectedFile = getExpectedFile(testFile, isCopyPaste = true, pluginMode)
        val actualText = myFixture.file.text
        KotlinTestUtils.assertEqualsToFile(expectedFile, actualText)
    }
}
