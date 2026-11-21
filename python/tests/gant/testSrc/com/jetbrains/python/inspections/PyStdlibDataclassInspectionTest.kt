// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections

import com.intellij.idea.TestFor
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import org.junit.jupiter.api.Test

@Subsystems.Inspections
@Layers.Functional
@TestFor(classes = [PyStdlibDataclassInspection::class])
class PyStdlibDataclassInspectionTest : PyCodeInsightTestCase() {
  @Test
  @TestFor(issues = ["PY-57611"])
  fun `default factory of a dataclass without init is reported`() = test("""
    from dataclasses import dataclass, field

    @dataclass(init=False)
    class A:
        a: int = field(default=0)
        b: int = field(default_factory=int)
    #                                  ^^^ WARNING Attribute 'b' is not set because the class has no generated '__init__'
    """.trimIndent())

  @Test
  @TestFor(issues = ["PY-57611"])
  fun `default factory of a dataclass with its own init is not reported`() = test("""
    from dataclasses import dataclass, field

    @dataclass(init=False)
    class A:
        b: int = field(default_factory=int)

        def __init__(self) -> None:
            self.b = 1
    """.trimIndent())

  @Test
  @TestFor(issues = ["PY-57611"])
  fun `default factory of a dataclass with a generated init is not reported`() = test("""
    from dataclasses import dataclass, field

    @dataclass
    class A:
        b: int = field(default_factory=int)
    """.trimIndent())

  @Test
  @TestFor(issues = ["PY-57611"])
  fun `default factory of a field without init in a dataclass with a generated init is not reported`() = test("""
    from dataclasses import dataclass, field

    @dataclass
    class A:
        b: list[int] = field(init=False, default_factory=list)
    """.trimIndent())

  @Test
  @TestFor(issues = ["PY-57611"])
  fun `default factory of a dataclass that inherits an init is not reported`() = test("""
    from dataclasses import dataclass, field

    class Base:
        def __init__(self) -> None:
            self.b = 1

    @dataclass(init=False)
    class A(Base):
        b: int = field(default_factory=int)
    """.trimIndent())

  @Test
  @TestFor(issues = ["PY-57611"])
  fun `default factory of a dataclass that inherits a generated init is not reported`() = test("""
    from dataclasses import dataclass, field

    @dataclass
    class Base:
        b: int = field(default_factory=int)

    @dataclass(init=False)
    class A(Base):
        b: int = field(default_factory=int)
    """.trimIndent())

  @Test
  @TestFor(issues = ["PY-57611"])
  fun `default factory of a class variable in a dataclass without init has only its own error`() = test("""
    from dataclasses import dataclass, field
    from typing import ClassVar

    @dataclass(init=False)
    class A:
        b: ClassVar[int] = field(default_factory=int)
    #                                            ^^^ ERROR Field cannot have a default factory
    """.trimIndent())
}
