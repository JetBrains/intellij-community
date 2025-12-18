// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.slicer

import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.psi.PsiDocumentManager
import com.intellij.slicer.SliceLanguageSupportProvider
import org.jetbrains.kotlin.idea.multiplatform.setupMppProjectFromDirStructure
import org.jetbrains.kotlin.idea.test.AbstractMultiModuleTest
import org.jetbrains.kotlin.idea.test.IDEA_TEST_DATA_DIR
import org.jetbrains.kotlin.idea.test.KotlinTestUtils
import org.jetbrains.kotlin.idea.test.extractMarkerOffset
import org.jetbrains.kotlin.idea.test.findFileWithCaret
import org.jetbrains.kotlin.psi.KtFile
import java.io.File

abstract class AbstractSlicerMultiplatformTest : AbstractMultiModuleTest() {
    override fun getTestDataDirectory() = IDEA_TEST_DATA_DIR.resolve("slicer/mpp")

    protected open fun doTest(filePath: String) {
        val testRoot = File(filePath)
        setupMppProjectFromDirStructure(testRoot)

        val file = project.findFileWithCaret() as KtFile
        val document = PsiDocumentManager.getInstance(myProject).getDocument(file)!!
        val offset = document.extractMarkerOffset(project, "<caret>")

        testSliceFromOffset(file, offset) { _, rootNode ->
            KotlinTestUtils.assertEqualsToFile(getResultsFile(testRoot), ActionUtil.underModalProgress(project, "") {  buildTreeRepresentation(rootNode) })
        }
    }

    protected open fun getResultsFile(testRoot: File): File = testRoot.resolve("results.txt")

    protected abstract fun createSliceProvider(): SliceLanguageSupportProvider
}