// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.requirements

import com.intellij.openapi.actionSystem.IdeActions

class RequirementsCommenterTest : PythonDependencyTestCase() {

  fun testComment() = doTest(
    before = """
      |mypy<caret>
    """.trimMargin(),
    after = """
      |#mypy
    """.trimMargin()
  )

  fun testUncomment() = doTest(
    before = """
      |# mypy<caret>
    """.trimMargin(),
    after = """
      | mypy
    """.trimMargin()
  )

  fun testCommentMultiline() = doTest(
    before = """
      |mypy
      |<selection>mypy-extensions
      |tomli</selection>
    """.trimMargin(),
    after = """
      |mypy
      |#mypy-extensions
      |#tomli
    """.trimMargin()
  )

  fun testUncommentMultiline() = doTest(
    before = """
      |<selection># mypy
      |#tomli</selection>
    """.trimMargin(),
    after = """
      | mypy
      |tomli
    """.trimMargin()
  )

  fun testMixedSelectionCommentsAll() = doTest(
    before = """
      |<selection>#mypy
      |tomli</selection>
    """.trimMargin(),
    after = """
      |##mypy
      |#tomli
    """.trimMargin()
  )

  fun testSelectionWithBlankLines() = doTest(
    before = """
      |<selection>mypy
      |
      |tomli</selection>
    """.trimMargin(),
    after = """
      |#mypy
      |#
      |#tomli
    """.trimMargin()
  )

  private fun doTest(before: String, after: String) {
    myFixture.configureByText("requirements.txt", before)
    myFixture.performEditorAction(IdeActions.ACTION_COMMENT_LINE)
    myFixture.checkResult(after, true)
  }
}
