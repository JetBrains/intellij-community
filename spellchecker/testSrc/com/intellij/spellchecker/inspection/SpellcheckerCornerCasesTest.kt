// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.spellchecker.inspection

import com.intellij.openapi.Disposable
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.SpellCheckingEditorCustomizationProvider
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileTypes.FileTypes
import com.intellij.spellchecker.SpellCheckerManager
import com.intellij.ui.EditorTextFieldProvider
import junit.framework.TestCase


class SpellcheckerCornerCasesTest : SpellcheckerInspectionTestCase() {
  fun `test a lot of mistakes in united word suggest`() {
    //should not end up with OOM
    val manager = SpellCheckerManager.getInstance(project)
    val suggestions = manager.getSuggestions("MYY_VERRY_LOOONG_WORDD_WOTH_A_LOTTT_OFFF_MISAKES")
    TestCase.assertTrue(suggestions.isNotEmpty())
  }

  fun `test highlighting works in editor text field with a customization`() {
    myFixture.enableInspections(*getInspectionTools())

    val customization = SpellCheckingEditorCustomizationProvider.getInstance().enabledCustomization
    assertNotNull(customization)

    val field = EditorTextFieldProvider.getInstance().getEditorField(FileTypes.PLAIN_TEXT.language, project, setOf(customization))
    field.addNotify()
    disposeOnTearDown(Disposable { field.removeNotify() })

    val document = field.editor!!.document
    WriteCommandAction.runWriteCommandAction(project) {
      document.setText("A <TYPO descr=\"Typo: In word 'misake'\">misake</TYPO>")
    }

    myFixture.configureFromExistingVirtualFile(FileDocumentManager.getInstance().getFile(document)!!)
    myFixture.checkHighlighting()
  }
}