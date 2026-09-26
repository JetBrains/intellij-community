package com.intellij.mermaid.lang.psi

import com.intellij.mermaid.lang.MermaidBaseTestCase
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.util.TextRange
import com.intellij.psi.ElementManipulators
import com.intellij.psi.impl.DebugUtil
import com.intellij.psi.util.parentOfType
import java.io.File

class MarkdownElementManipulatorTest : MermaidBaseTestCase("psi") {
  private val newContent = """
    Line 1
      Line 2
      Line 3
  """.trimIndent()

  fun `test replace`() {
    val testName = getTestName(true)
    val file = myFixture.configureByFile("${testName}_before.mermaid")

    val element = file.findElementAt(myFixture.caretOffset)?.parentOfType<MermaidMarkdownValue>()
    assertNotNull(element)

    val manipulator = ElementManipulators.getNotNullManipulator(element!!)
    WriteCommandAction.runWriteCommandAction(element.project) {
      manipulator.handleContentChange(element, TextRange.create(0, element.textLength), newContent)
    }
    myFixture.checkResultByFile("${testName}_after.mermaid")

    val actual = DebugUtil.psiToString(file, true, true)
    assertSameLinesWithFile("${testDataPath}${File.separatorChar}${testName}.txt", actual)
  }
}
