// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.testing

import com.intellij.idea.TestFor
import com.jetbrains.python.allure.Components
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import org.junit.jupiter.api.Test

/**
 * Types and highlighting of a `unittest.mock` mock with a spec.
 */
@Subsystems.TestRunner
@Components.Unittest
@Layers.Functional
class PyMockSpecTypeTest : PyCodeInsightTestCase() {

  @Test
  @TestFor(issues = ["PY-32282"])
  fun `spec class gives the mock and the spec members`() = test("""
    from unittest.mock import MagicMock

    class A:
        def foo(self, x: str) -> int: ...

    m = MagicMock(spec=A)
    #\ TYPE MagicMock (A)
    m.foo("a")
    m.assert_called()
    m.bar() # WARNING Unresolved attribute reference 'bar' for class 'MagicMock (A)'
    """.trimIndent())

  @Test
  @TestFor(issues = ["PY-32282"])
  fun `spec from spec_set and the first positional argument`() = test("""
    from unittest.mock import Mock

    class A: ...

    s = Mock(spec_set=A)
    #\ TYPE Mock (A)
    p = Mock(A)
    #\ TYPE Mock (A)
    """.trimIndent())

  @Test
  @TestFor(issues = ["PY-32282"])
  fun `attribute imitates the spec member`() = test("""
    from unittest.mock import MagicMock

    class A:
        a: int
        def foo(self) -> int: ...

    m = MagicMock(spec=A())
    attr = m.a
    #\ TYPE MagicMock (int)
    method = m.foo
    #\ TYPE MagicMock (() -> int)
    result = m.foo()
    #\ TYPE MagicMock (int)
    m.foo.assert_called()
    _ = m.foo.anything
    """.trimIndent())

  @Test
  @TestFor(issues = ["PY-89004"])
  fun `attribute outside the mock and the spec is unresolved`() = test("""
    from unittest.mock import Mock

    class A:
        a: int

    a = Mock(spec=A())
    _ = a.a
    _ = a.b # WARNING Unresolved attribute reference 'b' for class 'Mock (A)'
    a.assert_called()

    w = Mock(wraps=A())
    _ = w.a
    _ = w.b # WARNING Unresolved attribute reference 'b' for class 'Mock (A)'
    w.assert_called()
    """.trimIndent())

  @Test
  @TestFor(issues = ["PY-89004"])
  fun `mock is assignable where its spec is`() = test("""
    from unittest.mock import MagicMock, Mock, NonCallableMock

    class A:
        a: int

    class B: ...

    a: A
    a = MagicMock(spec=A())
    a = MagicMock(wraps=A())
    a = MagicMock(wraps=1) # WARNING Expected type 'A', got 'MagicMock (int)' instead
    m: Mock = MagicMock(spec=A)
    n: NonCallableMock = MagicMock(spec=A)
    o: object = MagicMock(spec=A)
    b: B = MagicMock(spec=A) # WARNING Expected type 'B', got 'MagicMock (A)' instead
    """.trimIndent())

  @Test
  @TestFor(issues = ["PY-89004"])
  fun `call on an attribute of a wraps mock returns the real result`() = test("""
    from unittest.mock import Mock

    class A:
        def foo(self) -> int: ...

    w = Mock(wraps=A())
    r = w.foo()
    #\ TYPE int
    """.trimIndent())

  @Test
  @TestFor(issues = ["PY-32282"])
  fun `assignment to a mock with a frozen dataclass spec`() = test("""
    from dataclasses import dataclass
    from unittest.mock import MagicMock

    @dataclass(frozen=True)
    class Cfg:
        x: int

    m = MagicMock(spec=Cfg)
    m.x = 1
    """.trimIndent())

  @Test
  @TestFor(issues = ["PY-32282"])
  fun `mock with a class spec stands in for the class`() = test("""
    from typing import Callable
    from unittest.mock import MagicMock, NonCallableMock

    class Client: ...

    def make(cls: type[Client]) -> None: ...
    def run(factory: Callable[[], Client]) -> None: ...

    make(MagicMock(spec=Client))
    run(MagicMock(spec=Client))
    make(MagicMock(spec=int)) # WARNING Expected type 'type[Client]', got 'MagicMock (int)' instead
    m = MagicMock(spec=Client)
    m()
    n = NonCallableMock(spec=Client)
    n() # WARNING 'n' is not callable
    """.trimIndent())

  @Test
  @TestFor(issues = ["PY-32282"])
  fun `property of the spec`() = test("""
    from unittest.mock import MagicMock

    class A:
        @property
        def value(self) -> int: ...

    def f(x: int) -> None: ...

    m = MagicMock(spec=A)
    v = m.value
    #\ TYPE MagicMock (int)
    f(m.value)
    """.trimIndent())

  @Test
  @TestFor(issues = ["PY-32282"])
  fun `list and None specs give no spec`() = test("""
    from unittest.mock import Mock

    names = Mock(spec=["method_a"])
    #\ TYPE Mock
    names.method_a()
    none = Mock(spec=None)
    #\ TYPE Mock
    _ = none.foo
    """.trimIndent())

  @Test
  @TestFor(issues = ["PY-32282"])
  fun `type variable binds to the mock`() = test("""
    from typing import TypeVar
    from unittest.mock import MagicMock

    T = TypeVar("T")

    def ident(x: T) -> T: ...

    class A: ...

    r = ident(MagicMock(spec=A))
    #\ TYPE MagicMock (A)
    r.assert_called()
    """.trimIndent())

  @Test
  @TestFor(issues = ["PY-32282"])
  fun `attribute mock class follows CPython`() = test("""
    from unittest.mock import AsyncMock, NonCallableMagicMock

    class A:
        def foo(self) -> int: ...
        async def bar(self) -> int: ...

    n = NonCallableMagicMock(spec=A)
    foo = n.foo
    #\ TYPE MagicMock (() -> int)
    n.foo()
    bar = n.bar
    #\ TYPE AsyncMock (() -> CoroutineType[Unknown, Unknown, int])
    a = AsyncMock(spec=A)
    sync_foo = a.foo
    #\ TYPE MagicMock (() -> int)
    """.trimIndent())

  @Test
  @TestFor(issues = ["PY-32282"])
  fun `mock constructor arguments are checked`() = test("""
    from unittest.mock import NonCallableMock

    class A: ...

    m = NonCallableMock(spec=A, name=123) # WARNING Expected type 'str | None', got 'Literal[123]' instead
    """.trimIndent())

  @Test
  @TestFor(issues = ["PY-32282"])
  fun `attribute and return value of a spec mock have no spec`() = test("""
    from unittest.mock import MagicMock

    class A:
        a: int
        def foo(self) -> int: ...

    m = MagicMock(spec=A)
    _ = m.a.anything
    _ = m.foo().anything
    """.trimIndent())

  @Test
  @TestFor(issues = ["PY-32282"])
  fun `call on an async attribute returns a coroutine`() = test("""
    from unittest.mock import NonCallableMagicMock

    class A:
        async def bar(self) -> int: ...

    n = NonCallableMagicMock(spec=A)
    r = n.bar()
    #\ TYPE CoroutineType[Unknown, Unknown, int]
    """.trimIndent())
}
