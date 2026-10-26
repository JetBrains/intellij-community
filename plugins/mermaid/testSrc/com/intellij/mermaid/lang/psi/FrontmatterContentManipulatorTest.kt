package com.intellij.mermaid.lang.psi

import com.intellij.mermaid.lang.mermaidFixture
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.util.TextRange
import com.intellij.psi.ElementManipulators
import com.intellij.psi.impl.DebugUtil
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import java.io.File

@TestApplication
class FrontmatterContentManipulatorTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val mermaid by mermaidFixture(projectFixture, "psi")

  private val newContent = """
    
    title: Title
    
    some: Value
    
  """.trimIndent()

  @Test
  fun `test frontmatter`() {
    val testName = mermaid.getTestName(true)
    val file = mermaid.fixture.configureByFile("${testName}_before.mermaid")

    val element = runReadActionBlocking { file.traverse().filterIsInstance<MermaidFrontmatterContent>().firstOrNull() }
    assertNotNull(element)

    val manipulator = ElementManipulators.getNotNullManipulator(element!!)
    WriteCommandAction.runWriteCommandAction(mermaid.project) {
      manipulator.handleContentChange(element, TextRange.create(0, element.textLength), newContent)
    }
    mermaid.fixture.checkResultByFile("${testName}_after.mermaid")

    val actual = runReadActionBlocking { DebugUtil.psiToString(file, true, true) }
    PlatformTestUtil.assertSameLinesWithFile("${mermaid.testDataPath}${File.separatorChar}${testName}.txt", actual)
  }
}
