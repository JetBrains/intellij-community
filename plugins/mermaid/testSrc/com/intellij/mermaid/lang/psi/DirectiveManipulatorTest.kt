package com.intellij.mermaid.lang.psi

import com.intellij.mermaid.lang.MermaidBaseTestCase
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.util.TextRange
import com.intellij.psi.ElementManipulators
import com.intellij.psi.impl.DebugUtil
import java.io.File

class DirectiveManipulatorTest : MermaidBaseTestCase("psi") {
  private val newContent = """
    {
      init: {
        "theme": "forest"
      }
    }
  """.trimIndent()

  fun `test directive`() {
    val testName = getTestName(true)
    val file = myFixture.configureByFile("${testName}_before.mermaid")

    val element = file.traverse().filterIsInstance<MermaidDirectiveValue>().firstOrNull()
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
