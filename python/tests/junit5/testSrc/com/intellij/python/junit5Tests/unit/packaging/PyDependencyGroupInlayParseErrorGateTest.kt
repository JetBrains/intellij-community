// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.packaging

import com.intellij.openapi.application.readAction
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.jetbrains.python.packaging.toolwindow.marker.PyDependencyGroupInlayHintsProvider
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.toml.lang.psi.TomlFileType

/**
 * PY-91037: the "+ Add package" inlay must not appear while `pyproject.toml` is syntactically
 * broken. `PyDependencyGroupInlayHintsProvider.getCollectorFor` gates the entire file on
 * [PyDependencyGroupInlayHintsProvider.hasParseErrors]; these tests pin the gate.
 *
 * The regression was: TOML parser recovery preserves a valid-looking `TomlKeySegment` for a
 * bare `test` line under `[dependency-groups]`. The segment-level resolver happily classifies
 * it as a group, so the inlay was drawn — but clicking it eventually shelled out to
 * `uv add` / `poetry add`, both of which refused the malformed file with a raw stderr trace.
 * Better no affordance than one that corrupts the file.
 */
@TestApplication
internal class PyDependencyGroupInlayParseErrorGateTest {
  private val projectFixture = projectFixture()
  private val project get() = projectFixture.get()

  @Test
  fun `well-formed pyproject with dependency groups has no parse errors`() = timeoutRunBlocking {
    val file = readAction { tomlFile(
      """
      [project]
      name = "pkg"

      [dependency-groups]
      test = ["pytest"]
      lint = ["ruff"]
      """.trimIndent()
    ) }

    assertFalse(readAction { PyDependencyGroupInlayHintsProvider.hasParseErrors(file) },
                "complete PEP 735 groups must not gate the inlay")
  }

  @Test
  fun `incomplete PEP 735 dependency group entry is treated as parse error`() = timeoutRunBlocking {
    val file = readAction { tomlFile(
      """
      [dependency-groups]
      test
      """.trimIndent()
    ) }

    assertTrue(readAction { PyDependencyGroupInlayHintsProvider.hasParseErrors(file) },
               "bare `test` line without `=` must trip the inlay gate — this is the PY-91037 regression")
  }

  @Test
  fun `unclosed inline array under dependency-groups is a parse error`() = timeoutRunBlocking {
    val file = readAction { tomlFile(
      """
      [dependency-groups]
      test = ["pytest",
      """.trimIndent()
    ) }

    assertTrue(readAction { PyDependencyGroupInlayHintsProvider.hasParseErrors(file) },
               "half-written array literal must trip the inlay gate")
  }

  @Test
  fun `unclosed table header trips the inlay gate`() = timeoutRunBlocking {
    val file = readAction { tomlFile(
      """
      [dependency-groups
      test = ["pytest"]
      """.trimIndent()
    ) }

    assertTrue(readAction { PyDependencyGroupInlayHintsProvider.hasParseErrors(file) },
               "missing `]` on the header must trip the inlay gate")
  }

  private fun tomlFile(text: String): PsiFile =
    PsiFileFactory.getInstance(project).createFileFromText("pyproject.toml", TomlFileType, text)
}
