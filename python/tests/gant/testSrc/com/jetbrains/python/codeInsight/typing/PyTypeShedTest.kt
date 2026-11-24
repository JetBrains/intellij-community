// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.typing

import com.intellij.idea.TestFor
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.PyDocStringOwner
import com.jetbrains.python.pyi.PyiFileType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files
import kotlin.io.path.extension
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.readText
import kotlin.streams.asSequence

@Subsystems.CodeInsight
@Layers.Functional
@TestFor(issues = ["PY-83660"])
class PyTypeShedTest : PyCodeInsightTestCase() {

  @Test
  fun `stdlib module names hold the modules and packages of the language level`() {
    val names = PyTypeShed.findStdlibModuleNames(LanguageLevel.PYTHON310)
    assertThat(names).contains("builtins", "math", "_socket", "collections")
    assertThat(names).doesNotContain("tomllib", "VERSIONS", "@python2", "@tests")
  }

  @Test
  fun `stdlib module names hold a module from its first language level`() {
    assertThat(PyTypeShed.findStdlibModuleNames(LanguageLevel.PYTHON311)).contains("tomllib")
  }

  @Test
  @TestFor(issues = ["PY-94383"])
  fun `stdlib docstrings are indented to the body of their definition`() {
    val stdlib = PyTypeShed.directory!!.toNioPath().resolve("stdlib")
    val stubs = Files.walk(stdlib).use { paths -> paths.asSequence().filter { it.extension == "pyi" }.sorted().toList() }
    val misindented = stubs.flatMap { path ->
      val text = path.readText()
      runReadAction {
        val file = PsiFileFactory.getInstance(myFixture.project).createFileFromText(path.fileName.toString(), PyiFileType.INSTANCE, text)
        PsiTreeUtil.findChildrenOfType(file, PyDocStringOwner::class.java).mapNotNull { owner ->
          val docstring = owner.docStringExpression ?: return@mapNotNull null
          // black does not indent a docstring with a backslash at a line end, such as the one of smtplib.SMTP.sendmail
          if (LINE_END_BACKSLASH.containsMatchIn(docstring.text)) return@mapNotNull null
          val start = docstring.textRange.startOffset
          val indent = text.substring(StringUtil.lastIndexOf(text, '\n', 0, start) + 1, start)
          val continuationLines = docstring.text.lines().drop(1).filter { it.isNotBlank() }
          if (continuationLines.all { it.startsWith(indent) }) null
          else "${stdlib.relativize(path).invariantSeparatorsPathString}:${StringUtil.offsetToLineNumber(text, start) + 1}"
        }
      }
    }
    assertThat(misindented.take(20))
      .describedAs("${misindented.size} docstrings have a line that starts left of the docstring")
      .isEmpty()
  }

  private companion object {
    val LINE_END_BACKSLASH = Regex("""\\\s*\n""")
  }
}
