// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections.typeignore

import com.intellij.idea.TestFor
import com.jetbrains.python.PyPsiBundle
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

private val LINE_MESSAGE = PyPsiBundle.message("INSP.type.ignore.without.code.line")
private val FILE_MESSAGE = PyPsiBundle.message("INSP.type.ignore.without.code.file")

@Subsystems.Inspections
@Layers.Functional
@PyCodeInsightTestCase.TestInspections(enableInspections = [PyTypeIgnoreWithoutCodeInspection::class])
@TestFor(issues = ["PY-90290"], classes = [PyTypeIgnoreWithoutCodeInspection::class])
class PyTypeIgnoreWithoutCodeInspectionTest : PyCodeInsightTestCase() {

  @Test
  fun `bare ignore on line flagged`() =test("""
    x = 1  # type: ignore
    #      ^^^^^^^^^^^^^^ WARNING $LINE_MESSAGE
    """)


  @Test
  fun `empty brackets flagged`() = test("""
    x = 1  # type: ignore[]
    #      ^^^^^^^^^^^^^^^^ WARNING $LINE_MESSAGE
    """)

  @Test
  fun `foreign code only flagged`() = test("""
    x = 1  # type: ignore[attr-defined]
    #      ^^^^^^^^^^^^^^^^^^^^^^^^^^^^ WARNING The comment names no known inspection code. It suppresses every inspection on the line.
    """)

  @Test
  fun `empty pycharm namespace flagged`() = test("""
    x = 1  # type: ignore[pycharm:]
    #      ^^^^^^^^^^^^^^^^^^^^^^^^ WARNING $LINE_MESSAGE
    """)

  @Test
  fun `case insensitive ignore flagged`() = test("""
    x = 1  # TyPe: IGnore
    #      ^^^^^^^^^^^^^^ WARNING $LINE_MESSAGE
    """)

  @Test
  fun `plain text tail flagged`() = test("""
    x = 1  # type: ignore this is a note
    #      ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ WARNING $LINE_MESSAGE
    """)

  @Test
  fun `recognized code not flagged`() = test("""
    x = 1  # type: ignore[PyTypeChecker]
    """)

  @Test
  fun `pycharm namespace code not flagged`() = test("""
    x = 1  # type: ignore[pycharm:PyTypeChecker]
    """)

  @Test
    /** One known code is enough, and it still suppresses the inspection that it names. */
  fun `foreign code beside known code not flagged`() = test("""
    x: int = 'foo'  # type: ignore[attr-defined, PyTypeChecker]
    """)


  /** The comment keeps its effect on every other inspection, and the report goes on the comment. */
  @Test
  fun `flagged comment still suppresses other inspections`() = test("""
    x: int = 'foo'  # type: ignore
    #               ^^^^^^^^^^^^^^ WARNING $LINE_MESSAGE
    """)


  @Test
  fun `file level bare ignore flagged`() = test("""
    # type: ignore
    #^^^^^^^^^^^^^ WARNING $FILE_MESSAGE
    x = 1
    """)


  @Test
  fun `file level ignore after shebang flagged`() = test("""
    #!/usr/bin/env python
    # type: ignore
    #^^^^^^^^^^^^^ WARNING $FILE_MESSAGE
    x = 1
    """)


  @Test
    /** A comment after a statement is not file-level, so it suppresses nothing and gets no report. */
  fun `comment after docstring not flagged`() = test("""
    "Module doc."
    # type: ignore
    x = 1
    """)


  @Test
  fun `standalone comment not flagged`() = test("""
    x = 1
    # type: ignore
    y = 2
    """)


  @Test
  fun `plain comment not flagged`() = test("""
    x = 1  # a plain comment
    """)


  @Test
  fun `own code suppresses the report`() = test("""
    x = 1  # type: ignore[PyTypeIgnoreWithoutCode]
    """)


  @Test
  /** A file-level opt-out reaches a line-level comment that names no code. */
  fun `file level own code suppresses the line report`() = test("""
    # type: ignore[PyTypeIgnoreWithoutCode]
    x = 1  # type: ignore
    """)


  @Test
  /**
   * A bare `# noqa` suppresses every inspection on the line, this one included. The flake8 suppressor stays
   * a separate mechanism, so it keeps no exception for this inspection.
   */
  fun `noqa tail suppresses the report`() = test("""
    x = 1  # type: ignore # noqa
    """)


  @Test
  /** The suppressor compares the tool id against the constant, so the two must not diverge. */
  fun `suppress id matches the tool id`() {
    assertEquals(PyTypeIgnoreWithoutCodeInspection.SUPPRESS_ID, PyTypeIgnoreWithoutCodeInspection().id)
  }
}
