// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.navigation

import com.intellij.codeInsight.navigation.GotoTargetHandler
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.jetbrains.kotlin.idea.multiplatform.setupMppProjectFromDirStructure
import org.jetbrains.kotlin.idea.test.AbstractMultiModuleTest
import org.jetbrains.kotlin.idea.test.extractMarkerOffset
import org.jetbrains.kotlin.idea.test.findFileWithCaret
import java.io.File

abstract class AbstractKotlinNavigationMultiModuleTest : AbstractMultiModuleTest() {
    protected abstract fun doNavigate(editor: Editor, file: PsiFile): GotoTargetHandler.GotoData

    protected fun doTest(testDataDir: String) {
        setupMppProjectFromDirStructure(File(testDataDir))
        val file = project.findFileWithCaret()

        val doc = PsiDocumentManager.getInstance(myProject).getDocument(file)!!
        val offset = doc.extractMarkerOffset(project, "<caret>")
        val editor = EditorFactory.getInstance().createEditor(doc, myProject)
        editor.caretModel.moveToOffset(offset)
        try {
            val gotoData = doNavigate(editor, file)
            val documentText = editor.document.text
            val expectedReferences = if (pluginMode == KotlinPluginMode.K2 && documentText.contains("// K2_REF:")) {
                NavigationTestUtils.getExpectedReferences(documentText, "// K2_REF:")
            } else {
                NavigationTestUtils.getExpectedReferences(documentText, "// REF:")
            }
            NavigationTestUtils.assertGotoDataMatching(editor, gotoData, true, expectedReferences)
        } finally {
            EditorFactory.getInstance().releaseEditor(editor)
        }
    }
}