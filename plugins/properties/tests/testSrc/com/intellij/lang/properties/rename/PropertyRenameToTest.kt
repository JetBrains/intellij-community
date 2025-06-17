package com.intellij.lang.properties.rename

import com.intellij.lang.properties.psi.impl.PropertyKeyImpl
import com.intellij.spellchecker.inspections.SpellCheckingInspection
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.ProcessingContext

class PropertyRenameToTest : BasePlatformTestCase() {

  fun `test suggestions does not contain space symbol`() {
    val validator = PropertyKeyRenameInputValidator()
    val context = ProcessingContext()
    myFixture.configureByText("a.properties", "hellow<caret>orld=value")
    val element = myFixture.file.findElementAt(myFixture.editor.caretModel.offset)!! as PropertyKeyImpl

    assertTrue(validator.isInputValid("hello-world", element, context))
    assertTrue(validator.isInputValid("hello.world", element, context))
    assertTrue(validator.isInputValid("hello_world", element, context))
    assertFalse(validator.isInputValid("hello world", element, context))
  }

  fun `test rename action is enabled and visible`() {
    myFixture.configureByText("a.properties", "<TYPO descr=\"Typo: In word 'helloworld'\">hellow<caret>orld</TYPO>=value")
    myFixture.enableInspections(SpellCheckingInspection())
    myFixture.checkHighlighting()
    myFixture.getAvailableIntention("Typo: Rename to…") ?: error("RenameTo intention is not available")
  }
}
