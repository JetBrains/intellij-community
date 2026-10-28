// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.lsp.core

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.idea.TestFor
import com.intellij.openapi.util.TextRange
import com.intellij.python.zuban.zubanQuickFixes
import com.intellij.testFramework.runInEdtAndGet
import com.intellij.openapi.application.readAction
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import org.assertj.core.api.Assertions.assertThat
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * The PyCharm quick fixes for the diagnostics of an LSP tool that cannot resolve a reference.
 *
 * Each case puts the diagnostic on the range the tool really marks. The zuban ranges come from zuban 0.10.0.
 * It marks only the module name inside an import, while pyrefly marks the whole import element or the
 * whole `from` import.
 */
@TestFor(classes = [PyLspUnresolvedReferenceCodes::class], issues = ["PY-85009"])
class PyLspUnresolvedReferenceQuickFixesTest : PyCodeInsightTestCase() {
  @Nested
  inner class Zuban {
    @Test
    fun `an undefined name gets the fixes of the unresolved reference inspection`() {
      val texts = zubanFixTexts("x = true", "true", "name-defined")
      assertThat(texts).contains(PyPsiBundle.message("QFIX.replace.with.true.or.false", "True"), RENAME_REFERENCE)
    }

    @Test
    fun `an undefined name in a function can become a parameter`() {
      val texts = zubanFixTexts("def f():\n    print(undefined_var)", "undefined_var", "name-defined")
      assertThat(texts).contains(PyPsiBundle.message("QFIX.unresolved.reference.add.param", "undefined_var"), RENAME_REFERENCE)
    }

    @Test
    fun `a missing module in an import offers a rename`() {
      // zuban marks `flask`, the topmost qualifier of `flask.json`.
      assertThat(zubanFixTexts("import flask.json", "flask", "import-not-found")).contains(RENAME_REFERENCE)
    }

    @Test
    fun `a missing module in a from import offers a rename`() {
      assertThat(zubanFixTexts("from flask import Flask", "flask", "import-not-found")).contains(RENAME_REFERENCE)
    }

    @Test
    fun `a missing name of a module offers a rename and no install`() {
      val texts = zubanFixTexts("from os import nonexistent", "nonexistent", "attr-defined")
      assertThat(texts).contains(RENAME_REFERENCE).doesNotContain(installPackage("os"))
    }

    @Test
    fun `a missing attribute outside an import gets no fix`() {
      // `attr-defined` also marks an attribute of an object, which no import fix can help.
      assertThat(zubanFixTexts("x = 1\nx.nonexistent", "nonexistent", "attr-defined")).isEmpty()
    }

    @Test
    fun `a module without stubs offers the stub package of the hint`() {
      val message = "Library stubs not installed for \"requests\"\nHint: \"python3 -m pip install types-requests\""
      val texts = zubanFixTexts("import requests", "requests", "import-untyped", message)
      assertThat(texts).containsExactly(installPackage("types-requests"))
    }

    @Test
    fun `another code gets no fix`() {
      assertThat(zubanFixTexts("x: int = \"s\"", "\"s\"", "assignment")).isEmpty()
    }
  }

  @Nested
  inner class Pyrefly {
    private val codes = PyLspUnresolvedReferenceCodes(
      unknownName = setOf("unknown-name"),
      missingImport = setOf("missing-import"),
      missingModuleAttribute = setOf("missing-module-attribute"),
    )

    @Test
    fun `a missing module marked as the whole import element offers a rename`() {
      val texts = fixTexts("import flask.json", "flask.json", "missing-import") { file, diagnostic, range ->
        pyLspUnresolvedReferenceQuickFixes(file, diagnostic, range, codes)
      }
      assertThat(texts).contains(RENAME_REFERENCE)
    }

    @Test
    fun `a missing module marked as the whole from import offers a rename`() {
      val texts = fixTexts("from flask import Flask", "from flask import Flask", "missing-import") { file, diagnostic, range ->
        pyLspUnresolvedReferenceQuickFixes(file, diagnostic, range, codes)
      }
      assertThat(texts).contains(RENAME_REFERENCE)
    }
  }

  private fun zubanFixTexts(code: String, marked: String, diagnosticCode: String, message: String = "message"): List<String> =
    fixTexts(code, marked, diagnosticCode, message) { file, diagnostic, range -> zubanQuickFixes(file, diagnostic, range) }

  /** The texts of the fixes that [fixes] gives for a diagnostic of [diagnosticCode] on the first [marked] text of [code]. */
  private fun fixTexts(
    code: String,
    marked: String,
    diagnosticCode: String,
    message: String = "message",
    fixes: (com.intellij.psi.PsiFile, Diagnostic, TextRange) -> List<IntentionAction>,
  ): List<String> = runInEdtAndGet {
    val file = myFixture.configureByText("a.py", code)
    val start = code.indexOf(marked)
    check(start >= 0) { "'$marked' is not in the code" }
    val range = TextRange(start, start + marked.length)
    // The quick fixes read only the code and the message, so the LSP range may stay empty.
    val diagnostic = Diagnostic(Range(Position(0, 0), Position(0, 0)), message, DiagnosticSeverity.Error, "test", diagnosticCode)
    // The annotator calls the fixes inside its job. The install fix asks the package manager, which needs a job.
    runWithModalProgressBlocking(file.project, "") { readAction { fixes(file, diagnostic, range).map { it.text } } }
  }

  private companion object {
    val RENAME_REFERENCE: String = PyPsiBundle.message("QFIX.rename.unresolved.reference")

    fun installPackage(packageName: String): String =
      PyBundle.message("python.unresolved.reference.inspection.install.package", packageName)
  }
}
