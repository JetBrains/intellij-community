// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections

import com.intellij.idea.TestFor
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import org.junit.jupiter.api.Test

/**
 * The type checker checks the argument of a `__class_getitem__` call in a runtime expression.
 * It does not check a subscription in a type hint or in a base class expression.
 */
@TestFor(classes = [PyTypeCheckerInspection::class], issues = ["PY-83758"])
@Subsystems.Inspections
@Layers.Functional
class PyClassGetItemCallTypeCheckTest : PyCodeInsightTestCase() {

  @Test
  fun `type parameter in a base class expression`() = test("""
    class C1[T]:
        def __class_getitem__(cls, item: int) -> str: ...

    class C2[T](C1[T]):
        pass
    """)

  @Test
  fun `old-style type parameter in a base class expression`() = test("""
    from typing import Generic, TypeVar

    T = TypeVar("T")

    class C1(Generic[T]):
        def __class_getitem__(cls, item: type[object] | tuple[type[object], ...]) -> type: ...

    class C2(C1[T]):
        pass
    """)

  @Test
  fun `nested subscription in a base class expression`() = test("""
    class C1[T]:
        def __class_getitem__(cls, item: int) -> str: ...

    class C2[T](list[C1[T]]):
        pass
    """)

  @Test
  fun `quoted subscription in a base class expression`() = test("""
    class C1[T]:
        def __class_getitem__(cls, item: int) -> str: ...

    class C2[T](list["C1[T]"]):
        pass
    """)

  @Test
  fun `subscription in a call argument in a base class expression`() = test("""
    class C1[T]:
        def __class_getitem__(cls, item: int) -> str: ...

    def make_base(arg: object) -> type: ...

    class C2(make_base(C1[str])):
    #                     ^^^ WARNING Expected type 'int', got 'type[str]' instead
        pass
    """)

  @Test
  fun `subscription in a star argument of a class statement`() = test("""
    class C1[T]:
        def __class_getitem__(cls, item: int) -> type: ...

    class C2(*[C1[str]]):
    #             ^^^ WARNING Expected type 'int', got 'type[str]' instead
        pass
    """)

  @Test
  fun `subscription in the body of a class with a subscribed base class`() = test("""
    class C1[T]:
        def __class_getitem__(cls, item: int) -> str: ...

    class C2[T](C1[T]):
        x = C1[str]
    #          ^^^ WARNING Expected type 'int', got 'type[str]' instead
    """)

  @Test
  fun `subscription in a runtime expression`() = test("""
    class C1[T]:
        def __class_getitem__(cls, item: int) -> str: ...

    C1[str]
    #  ^^^ WARNING Expected type 'int', got 'type[str]' instead
    """)

  @Test
  fun `subscription in a keyword argument of a class statement`() = test("""
    class Meta(type):
        def __class_getitem__(cls, item: int) -> type: ...

    class C(metaclass=Meta[str]):
    #                      ^^^ WARNING Expected type 'int', got 'type[str]' instead
        pass
    """)
}
