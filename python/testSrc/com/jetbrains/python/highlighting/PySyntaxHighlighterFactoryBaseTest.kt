// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.highlighting

import com.intellij.idea.TestFor
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.extensionPointFixture
import com.intellij.testFramework.junit5.fixture.moduleFixture
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.junit5.fixture.sourceRootFixture
import com.intellij.testFramework.junit5.fixture.virtualFileFixture
import com.jetbrains.python.PyLanguageFacade
import com.jetbrains.python.PythonLanguage
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

/**
 * Checks that the factory picks the console highlighter only for a file that a [PyConsoleLexerDetector] accepts.
 */
@TestApplication
@TestFor(classes = [PySyntaxHighlighterFactoryBase::class])
@Subsystems.Editing
@Layers.Functional
class PySyntaxHighlighterFactoryBaseTest {
  private val projectFixture = projectFixture()
  private val sourceRootFixture = projectFixture.moduleFixture().sourceRootFixture()
  private val plainFileFixture = sourceRootFixture.virtualFileFixture("plain.py", "x = 1")
  private val consoleFileFixture = sourceRootFixture.virtualFileFixture(CONSOLE_FILE_NAME, "x = 1")
  private val detectorFixture = extensionPointFixture(PyConsoleLexerDetector.EP_NAME) {
    object : PyConsoleLexerDetector {
      override fun isConsoleFile(project: Project, file: VirtualFile): Boolean = file.name == CONSOLE_FILE_NAME
    }
  }

  @Test
  fun `console highlighter for a file that the detector accepts`() {
    val project = projectFixture.get()
    val file = consoleFileFixture.get()
    val factory = pythonFactory()
    assertSame(factory.getConsoleSyntaxHighlighter(project, file), factory.getSyntaxHighlighter(project, file))
  }

  @Test
  fun `file highlighter for a file that the detector rejects`() {
    val project = projectFixture.get()
    val file = plainFileFixture.get()
    val factory = pythonFactory()
    val level = PyLanguageFacade.INSTANCE.getEffectiveLanguageLevel(project, file)
    val highlighter = factory.getSyntaxHighlighter(project, file)
    assertSame(factory.getSyntaxHighlighterForLanguageLevel(level), highlighter)
    assertNotSame(factory.getConsoleSyntaxHighlighter(project, file), highlighter)
  }

  private fun pythonFactory(): PySyntaxHighlighterFactoryBase {
    detectorFixture.get()
    return SyntaxHighlighterFactory.getLanguageFactory().forLanguage(PythonLanguage.INSTANCE) as PySyntaxHighlighterFactoryBase
  }

  private companion object {
    const val CONSOLE_FILE_NAME = "console.py"
  }
}
