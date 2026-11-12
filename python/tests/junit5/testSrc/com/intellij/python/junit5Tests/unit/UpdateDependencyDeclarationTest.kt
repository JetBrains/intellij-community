// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.junit5Tests.unit

import com.intellij.openapi.application.readAction
import com.intellij.openapi.command.writeCommandAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFileFactory
import com.intellij.python.junit5Tests.framework.PyDefaultTestApplication
import com.intellij.python.junit5Tests.framework.metaInfo.TestClassInfo
import com.intellij.python.requirements.RequirementsFile
import com.intellij.python.requirements.RequirementsLanguage
import com.intellij.python.requirements.parser.psi.NameReq
import com.intellij.testFramework.common.timeoutRunBlocking
import com.jetbrains.python.requirements.inspections.quickfixes.UpdateDependencyDeclaration
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

@PyDefaultTestApplication
@TestClassInfo
internal class UpdateDependencyDeclarationTest(private val project: Project) {

  @ParameterizedTest(name = "{0} -> {1}")
  @CsvSource(
    delimiter = '|',
    value = [
      // == pins get bumped; whitespace around the operator is preserved.
      "mypy==1.0 | mypy==2.0",
      "mypy == 1.0 | mypy == 2.0",
      // ~= pins get bumped: the installed version satisfies compatibility.
      "mypy~=1.0 | mypy~=2.0",
      // Extras and environment markers survive the swap because only the Version token changes.
      "mypy[dmypy]==1.0 | mypy[dmypy]==2.0",
      "mypy==1.0 ; python_version >= '3.9' | mypy==2.0 ; python_version >= '3.9'",
    ],
  )
  fun `rewrites a single-pin version-token of a requirements entry`(input: String, expected: String) = timeoutRunBlocking {
    assertRewrite(input, expected)
  }

  @ParameterizedTest(name = "{0} is left intact")
  @CsvSource(
    value = [
      // Compound specifiers would become unsatisfiable (>=2.0,<2.0) or change meaning.
      "mypy>=1.0,<2.0",
      // != and non-pinnable operators are not safe to bump.
      "mypy>=1.0",
      "mypy!=1.0",
      // An unpinned requirement is intentionally open; the quickfix must not add a pin.
      "mypy",
      "mypy[extra]",
      // --hash entries must stay in sync with the artifact; drop would break --require-hashes.
      "mypy==1.0 --hash=sha256:deadbeef",
    ],
  )
  fun `leaves non-pinnable requirements entries untouched`(input: String) = timeoutRunBlocking {
    assertRewrite(input, input)
  }

  private suspend fun assertRewrite(input: String, expected: String) {
    val file = createRequirementsFile(input)
    val nameReq = readAction { file.children.filterIsInstance<NameReq>().single() }

    writeCommandAction(project, "test") {
      UpdateDependencyDeclaration.apply(nameReq, "2.0")
    }

    assertEquals(expected, readAction { file.text })
  }

  private suspend fun createRequirementsFile(text: String): RequirementsFile = readAction {
    PsiFileFactory.getInstance(project).createFileFromText("requirements.txt", RequirementsLanguage, text) as RequirementsFile
  }
}
