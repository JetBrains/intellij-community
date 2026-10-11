// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections

import com.intellij.idea.TestFor
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import com.jetbrains.python.inspections.unusedLocal.PyUnusedLocalVariableInspection
import org.junit.jupiter.api.Test

/** A quoted annotation or a type comment uses the local symbols that it refers to. */
@TestFor(issues = ["PY-93096"], classes = [PyUnusedLocalVariableInspection::class])
@Subsystems.Inspections
@Layers.Functional
@PyCodeInsightTestCase.TestInspections(enableInspections = [PyUnusedLocalVariableInspection::class])
class PyQuotedTypeHintUsageTest : PyCodeInsightTestCase() {

  @Test
  fun `quoted type parameter of a type alias is used`() = test("""
    type List[T] = list["T"]
    """)

  @Test
  fun `type parameter in a nested quoted type of a type alias is used`() = test("""
    type Mapping[T] = list["dict[str, T]"]
    """)

  @Test
  fun `quoted type parameter of a class is used`() = test("""
    class Box[T]:
        item: "T"
    """)

  @Test
  fun `quoted type parameter in a method of a generic class is used`() = test("""
    class Box[T]:
        def get(self) -> "list[T]": ...
    """)

  @Test
  fun `quoted type parameter of a function is used`() = test("""
    def first[T](items: "list[T]") -> "T":
        return items[0]
    """)

  @Test
  fun `quoted type parameter in a local variable annotation of a function is used`() = test("""
    def make[T]() -> None:
        items: "list[T]" = []
        print(items)
    """)

  @Test
  fun `type parameter in a type comment of a function is used`() = test("""
    def make[T]() -> None:
        items = []  # type: list[T]
        print(items)
    """)

  @Test
  fun `local class in a quoted annotation is used`() = test("""
    def make() -> None:
        class Local: ...
        item: "Local | None" = None
        print(item)
    """)

  @Test
  fun `quoted use of one type parameter does not hide another unused one`() = test("""
    type Pair[T, U] = tuple["T", int]
    #            └ WEAK-WARNING Type parameter 'U' is not used
    """)
}
