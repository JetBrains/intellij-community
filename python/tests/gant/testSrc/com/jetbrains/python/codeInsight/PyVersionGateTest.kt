// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight

import com.intellij.idea.TestFor
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.codeInsight.typing.PyTypeShed
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.impl.PyFileImpl
import org.junit.jupiter.api.Test

/**
 * A module or a name is available only on the language levels that its source gives it.
 * Typeshed `stdlib/VERSIONS` gives the range of a standard library module.
 * The `sys.version_info` checks around a definition give the range of a name.
 * This applies also when a stub adds the name to `__all__` under such a check.
 */
@TestFor(classes = [PyTypeShed::class, PyFileImpl::class])
@Subsystems.CodeInsight
@Layers.Functional
class PyVersionGateTest : PyCodeInsightTestCase() {

  @TestFor(issues = ["PY-62799"])
  @Test
  @TestCaseOptions(languageLevel = LanguageLevel.PYTHON312)
  fun `annotationlib is not available on 3_12`() = test("""
    import annotationlib # ERROR FIXME No module named 'annotationlib'
    """)

  @TestFor(issues = ["PY-62799"])
  @Test
  @TestCaseOptions(languageLevel = LanguageLevel.PYTHON312)
  fun `compression zstd is not available on 3_12`() = test("""
    import compression.zstd # ERROR FIXME No module named 'compression'
    """)

  @TestFor(issues = ["PY-62799"])
  @Test
  @TestCaseOptions(languageLevel = LanguageLevel.PYTHON312)
  fun `string templatelib is not available on 3_12`() = test("""
    import string.templatelib # ERROR FIXME Cannot find reference 'templatelib' in 'string.pyi'
    """)

  @TestFor(issues = ["PY-62799"])
  @Test
  @TestCaseOptions(languageLevel = LanguageLevel.PYTHON312)
  fun `tomllib is available on 3_12`() = test("""
    import tomllib
    """)

  @TestFor(issues = ["PY-62799"])
  @Test
  @TestCaseOptions(languageLevel = LanguageLevel.PYTHON312)
  fun `removed module asyncore is not available on 3_12`() = test("""
    import asyncore # ISSUES *
    """)

  @TestFor(issues = ["PY-91446"])
  @Test
  @TestCaseOptions(languageLevel = LanguageLevel.PYTHON312)
  fun `copy replace is not available on 3_12 in a from import`() = test("""
    from copy import replace # ERROR FIXME Cannot find reference 'replace' in 'copy.pyi'
    """)

  @TestFor(issues = ["PY-91446"])
  @Test
  @TestCaseOptions(languageLevel = LanguageLevel.PYTHON312)
  fun `copy replace is not available on 3_12 as an attribute`() = test("""
    import copy

    copy.replace # WARNING FIXME Cannot find reference 'replace' in 'copy.pyi'
    """)

  @TestFor(issues = ["PY-91446"])
  @Test
  @TestCaseOptions(languageLevel = LanguageLevel.PYTHON312)
  fun `typing ReadOnly is not available on 3_12`() = test("""
    from typing import ReadOnly # ERROR FIXME Cannot find reference 'ReadOnly' in 'typing.pyi'
    """)

  @TestFor(issues = ["PY-91446"])
  @Test
  @TestCaseOptions(languageLevel = LanguageLevel.PYTHON312)
  fun `warnings deprecated is not available on 3_12`() = test("""
    from warnings import deprecated # ERROR FIXME Cannot find reference 'deprecated' in 'warnings.pyi'
    """)

  @Test
  @TestCaseOptions(languageLevel = LanguageLevel.PYTHON312)
  fun `sys name of 3_12 is available on 3_12`() = test("""
    import sys

    sys.last_exc
    """)

  @Test
  @TestCaseOptions(languageLevel = LanguageLevel.PYTHON312)
  fun `sys name of 3_14 is not available on 3_12`() = test("""
    import sys

    sys.remote_exec # WARNING Cannot find reference 'remote_exec' in 'sys'
    """)

  @TestFor(issues = ["PY-91446"])
  @Test
  @TestCaseOptions(languageLevel = LanguageLevel.PYTHON312)
  fun `version gated name of a user stub with __all__ is not available on 3_12`() = test("""
    from gated import new_func # ERROR FIXME Cannot find reference 'new_func' in 'gated'
    """,
    "gated.pyi" to """
    import sys

    __all__ = ["old_func"]

    def old_func() -> None: ...

    if sys.version_info >= (3, 13):
        __all__ += ["new_func"]

        def new_func() -> None: ...
    """)

  @TestFor(issues = ["PY-91446"])
  @Test
  @TestCaseOptions(languageLevel = LanguageLevel.PYTHON312)
  fun `version gated name of a user stub without __all__ is not available on 3_12`() = test("""
    from gated import new_func # ERROR Cannot find reference 'new_func' in 'gated'
    """,
    "gated.pyi" to """
    import sys

    def old_func() -> None: ...

    if sys.version_info >= (3, 13):
        def new_func() -> None: ...
    """)

  @Test
  @TestCaseOptions(languageLevel = LanguageLevel.PYTHON312)
  fun `name under a version check in the same file is not available on 3_12`() = test("""
    import sys

    if sys.version_info >= (3, 13):
        def new_func() -> int: ...

    new_func() # ISSUES *
    """)
}
