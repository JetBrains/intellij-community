// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.conversion.copy

import com.intellij.openapi.actionSystem.IdeActions
import org.jetbrains.kotlin.idea.AbstractCopyPasteTest
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.idea.test.KotlinWithJdkAndRuntimeLightProjectDescriptor
import java.io.File

abstract class AbstractLiteralTextToKotlinCopyPasteTest : AbstractCopyPasteTest() {
    override fun getProjectDescriptor() = KotlinWithJdkAndRuntimeLightProjectDescriptor.getInstance()

    protected open fun getExpectedFile(testFile: File): File =
        File(testFile.parent, testFile.nameWithoutExtension + ".expected.kt")

    fun doTest(unused: String) {
        val fileName = fileName()
        val targetFileName = fileName.replace(".txt", ".kt")

        myFixture.configureByFile(fileName)
        val fileText = myFixture.editor.document.text

        if (!myFixture.editor.selectionModel.hasSelection())
            myFixture.editor.selectionModel.setSelection(0, fileText.length)

        doPerformEditorAction(IdeActions.ACTION_COPY)

        configureTargetFile(targetFileName)

        doPerformEditorAction(IdeActions.ACTION_PASTE)

        val testFile = dataFile()
        KotlinTestUtils.assertEqualsToFile(getExpectedFile(testFile), myFixture.file.text)
    }

    protected open fun doPerformEditorAction(actionId: String) {
        myFixture.performEditorAction(actionId)
    }
}