// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.codeInsight.regexp

import com.intellij.idea.TestFor
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import org.junit.jupiter.api.Test

@Layers.Functional
@Subsystems.CodeInsight
@TestFor(issues = ["PY-90718"], classes = [PythonRegexpInjector::class])
class PyPydanticFieldPatternRegexpTest : PyCodeInsightTestCase() {
  @Test
  fun `the pattern of a pydantic field is a regexp`() = test(
    """
    from typing import Annotated, TypeAlias
    from pydantic import Field

    x: TypeAlias = Annotated[str, Field(pattern=r"^hello($")]
    #                                                     └ ERROR ')' expected
    y: Annotated[str, Field(min_length=1, pattern="a)")]
    #                                               └ ERROR Unmatched closing ')'
    """,
    *pydanticPackage,
  )

  @Test
  fun `the pattern of a pydantic field is a regexp also for a qualified or aliased name`() = test(
    """
    import pydantic
    from pydantic import Field as PydanticField

    pydantic.Field(pattern="a)")
    #                        └ ERROR Unmatched closing ')'
    PydanticField(pattern="a)")
    #                       └ ERROR Unmatched closing ')'
    """,
    *pydanticPackage,
  )

  @Test
  fun `the pattern of a pydantic field accepts the syntax of the Rust regex crate`() = test(
    """
    from pydantic import Field

    Field(pattern=r"^\p{L}+[[:digit:]](?<name>a)(?P<other>b)$(")
    #                                                         └ ERROR ')' expected
    """,
    *pydanticPackage,
  )

  @Test
  fun `only the pattern of a pydantic field is a regexp`() = test(
    """
    from pydantic import Field

    def Other(pattern=None): ...

    Field(description="(", pattern="a")
    Other(pattern="(")
    """,
    *pydanticPackage,
  )

  private val pydanticPackage = arrayOf(
    "pydantic/__init__.py" to "from pydantic.fields import Field",
    "pydantic/fields.py" to """
      from typing import overload

      @overload
      def Field(default: str, *, description=None, min_length=None, pattern=None, **extra) -> str: ...
      @overload
      def Field(default=..., *, description=None, min_length=None, pattern=None, **extra): ...
      def Field(default=..., *, description=None, min_length=None, pattern=None, **extra): ...
      """,
  )
}
