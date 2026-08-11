// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit.alsoWin.pyproject.psi

import com.intellij.openapi.application.readAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileSystemItem
import com.intellij.python.junit5Tests.framework.PyDefaultTestApplication
import com.intellij.python.junit5Tests.framework.metaInfo.TestClassInfo
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.common.timeoutRunBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * PY-90384: paths declared in `pyproject.toml` must be navigable.
 *
 * Every case pins one branch of `PyProjectTomlPathReferenceContributor`. The two negative cases matter as much as
 * the positive ones: they keep file references from spreading to every string that happens to look like a path.
 *
 * Offsets are located by line rather than by a `<caret>` marker so that one test data file can host several cases;
 * [offsetOf] fails loudly when a line is missing or ambiguous.
 */
@PyDefaultTestApplication
@TestClassInfo(contentRootPath = "python-pyproject/test")
@TestDataPath($$"$CONTENT_ROOT/../testData/navigation/pyprojectPaths")
internal class PyProjectTomlPathReferenceTest(private val sourceRoot: PsiDirectory) {

  @Test
  fun `uv workspace member resolves to the member directory`(): Unit = timeoutRunBlocking {
    assertResolvesTo(
      file = "pyproject.toml",
      line = """    "sub-projects/sub-project-a",""",
      token = "sub-project-a",
      expected = "sub-projects/sub-project-a",
    )
  }

  @Test
  fun `each segment of a member path resolves to its own directory`(): Unit = timeoutRunBlocking {
    assertResolvesTo(
      file = "pyproject.toml",
      line = """    "sub-projects/sub-project-a",""",
      token = "sub-projects",
      expected = "sub-projects",
    )
  }

  @Test
  fun `members declared in the inline workspace table resolve as well`(): Unit = timeoutRunBlocking {
    assertResolvesTo(
      file = "inline/pyproject.toml",
      line = """workspace = { members = ["packages/lib-a"] }""",
      token = "lib-a",
      expected = "inline/packages/lib-a",
    )
  }

  @Test
  fun `glob member resolves its prefix`(): Unit = timeoutRunBlocking {
    assertResolvesTo(
      file = "pyproject.toml",
      line = """    "sub-projects/*",""",
      token = "sub-projects",
      expected = "sub-projects",
    )
  }

  @Test
  fun `glob member has no reference on the pattern itself`(): Unit = timeoutRunBlocking {
    // A reference here could never resolve, and TomlUnresolvedReferenceInspection would report it as unresolved.
    assertNoReference(
      file = "pyproject.toml",
      line = """    "sub-projects/*",""",
      token = "*",
    )
  }

  @Test
  fun `uv workspace exclude resolves to the excluded directory`(): Unit = timeoutRunBlocking {
    assertResolvesTo(
      file = "pyproject.toml",
      line = """exclude = ["sub-projects/sub-project-b"]""",
      token = "sub-project-b",
      expected = "sub-projects/sub-project-b",
    )
  }

  @Test
  fun `uv path source resolves to the dependency directory`(): Unit = timeoutRunBlocking {
    assertResolvesTo(
      file = "pyproject.toml",
      line = """vendored = { path = "vendor/vendored-lib" }""",
      token = "vendored-lib",
      expected = "vendor/vendored-lib",
    )
  }

  @Test
  fun `poetry package include resolves against its from directory`(): Unit = timeoutRunBlocking {
    assertResolvesTo(
      file = "poetry-pkg/pyproject.toml",
      line = """packages = [{ include = "mypkg", from = "src" }]""",
      token = "mypkg",
      expected = "poetry-pkg/src/mypkg",
    )
  }

  @Test
  fun `poetry package from resolves against the project directory`(): Unit = timeoutRunBlocking {
    assertResolvesTo(
      file = "poetry-pkg/pyproject.toml",
      line = """packages = [{ include = "mypkg", from = "src" }]""",
      token = "src",
      expected = "poetry-pkg/src",
    )
  }

  @Test
  fun `poetry path dependency resolves to the sibling project`(): Unit = timeoutRunBlocking {
    assertResolvesTo(
      file = "poetry-pkg/pyproject.toml",
      line = """subproject-a = { path = "../sub-projects/sub-project-a", develop = true }""",
      token = "sub-project-a",
      expected = "sub-projects/sub-project-a",
    )
  }

  @Test
  fun `poetry group path dependency resolves to the sibling project`(): Unit = timeoutRunBlocking {
    assertResolvesTo(
      file = "poetry-pkg/pyproject.toml",
      line = """liba = { path = "../inline/packages/lib-a" }""",
      token = "lib-a",
      expected = "inline/packages/lib-a",
    )
  }

  @Test
  fun `path-like value of a non-path key gets no reference`(): Unit = timeoutRunBlocking {
    assertNoReference(
      file = "pyproject.toml",
      line = """description = "sub-projects/sub-project-a"""",
      token = "sub-project-a",
    )
  }

  @Test
  fun `poetry sdist include gets no reference`(): Unit = timeoutRunBlocking {
    // `tool.poetry.include` is a file pattern; only `tool.poetry.packages.include` is a package path.
    assertNoReference(
      file = "poetry-pkg/pyproject.toml",
      line = """include = ["docs"]""",
      token = "docs",
    )
  }

  private suspend fun assertResolvesTo(file: String, line: String, token: String, expected: String) {
    readAction {
      val psiFile = psiFile(file)
      val reference = psiFile.findReferenceAt(psiFile.offsetOf(line, token))
      assertThat(reference)
        .describedAs("No reference on '%s' of `%s` in %s", token, line.trim(), file)
        .isNotNull
      val resolved = reference!!.resolve()
      assertThat(resolved)
        .describedAs("Unresolved reference on '%s' of `%s` in %s", token, line.trim(), file)
        .isInstanceOf(PsiFileSystemItem::class.java)
      assertThat((resolved as PsiFileSystemItem).virtualFile)
        .describedAs("Wrong target for '%s' of `%s` in %s", token, line.trim(), file)
        .isEqualTo(virtualFile(expected))
    }
  }

  private suspend fun assertNoReference(file: String, line: String, token: String) {
    readAction {
      val psiFile = psiFile(file)
      assertThat(psiFile.findReferenceAt(psiFile.offsetOf(line, token)))
        .describedAs("Unexpected reference on '%s' of `%s` in %s", token, line.trim(), file)
        .isNull()
    }
  }

  /** Offset of [token] inside [line]; [line] must occur exactly once in the file, which keeps the lookup honest. */
  private fun PsiFile.offsetOf(line: String, token: String): Int {
    val lineStart = text.indexOf(line)
    check(lineStart >= 0) { "Line `$line` not found in $name:\n$text" }
    check(text.indexOf(line, lineStart + 1) < 0) { "Line `$line` occurs more than once in $name" }
    val tokenInLine = line.indexOf(token)
    check(tokenInLine >= 0) { "Token '$token' not found in line `$line`" }
    return lineStart + tokenInLine
  }

  private fun psiFile(relativePath: String): PsiFile =
    sourceRoot.manager.findFile(virtualFile(relativePath)) ?: error("No PSI file for $relativePath")

  private fun virtualFile(relativePath: String): VirtualFile =
    sourceRoot.virtualFile.findFileByRelativePath(relativePath) ?: error("No $relativePath in the test project")
}
