// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.inspections

import com.intellij.idea.TestFor
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * `*` and `**` unpacking in calls, displays and assignment targets.
 * Each test states one rule of the CPython runtime. A problem is expected only where the static types decide that the runtime raises.
 */
@Subsystems.Inspections
@Layers.Functional
@TestFor(issues = ["PY-32965"])
class PyUnpackingTest : PyCodeInsightTestCase() {

  @Nested
  inner class DoubleStarOperand {
    @Test
    fun `operand with keys and getitem is accepted`() = test("""
      class Mapped:
          def __init__(self):
              self.d = {"x": 1, "y": 2}

          def keys(self):
              return self.d.keys()

          def __getitem__(self, item):
              return self.d.__getitem__(item)

      class SubMapped(Mapped): ...

      def add(x, y):
          return x + y

      add(**Mapped())
      add(**SubMapped())
      expr = {**Mapped()}
      """.trimIndent())

    @Test
    fun `keys found by an instance attribute or getattr is accepted`() = test("""
      class InstanceKeys:
          def __init__(self, d: dict[str, int]):
              self.keys = d.keys

          def __getitem__(self, key: str) -> int: ...

      class DynamicKeys:
          def __getattr__(self, name: str): ...
          def __getitem__(self, key: str) -> int: ...

      class InterceptedKeys:
          def __getattribute__(self, name: str): ...
          def __getitem__(self, key: str) -> int: ...

      def f(**kwargs: int): ...

      f(**InstanceKeys({}))
      f(**DynamicKeys())
      f(**InterceptedKeys())
      """.trimIndent())

    @Test
    fun `operand without keys or getitem is reported`() = test("""
      class KeysOnly:
          def keys(self) -> list[str]: ...

      class GetItemOnly:
          def __getitem__(self, key: str) -> int: ...

      def f(**kwargs: int): ...

      def g(keys_only: KeysOnly, get_item_only: GetItemOnly, items: list[int], pairs: list[tuple[str, int]]):
          f(**keys_only) # WARNING Expected a mapping, got 'KeysOnly'
          f(**get_item_only) # WARNING Expected a mapping, got 'GetItemOnly'
          f(**None) # WARNING Expected a mapping, got 'None'
          f(**items) # WARNING Expected a mapping, got 'list[int]'
          f(**pairs) # WARNING Expected a mapping, got 'list[tuple[str, int]]'
      """.trimIndent())

    @Test
    fun `operand of unknown or generic type is not reported`() = test("""
      from typing import Any

      class Mapped:
          def keys(self) -> list[str]: ...
          def __getitem__(self, key: str) -> int: ...

      def f(**kwargs: int): ...

      def g[M: Mapped, T](anything: Any, bound: M, free: T):
          f(**anything)
          f(**bound)
          f(**free)
      """.trimIndent())

    @Test
    fun `call needs str keys`() = test("""
      from enum import Enum
      from typing import final

      class IntKeys:
          def keys(self) -> list[int]: ...
          def __getitem__(self, key: int) -> int: ...

      def f(**kwargs: int): ...

      @final
      class Token: ...

      class Color(Enum):
          RED = 1

      def g(ints: dict[int, int], raw: dict[bytes, int], either: dict[int | bytes, int], int_keys: IntKeys,
            tokens: dict[Token, int], colors: dict[Color, int]):
          f(**ints) # WARNING Expected a mapping with 'str' keys, got 'dict[int, int]'
          f(**raw) # WARNING Expected a mapping with 'str' keys, got 'dict[bytes, int]'
          f(**either) # WARNING Expected a mapping with 'str' keys, got 'dict[int | bytes, int]'
          f(**int_keys) # WARNING Expected a mapping with 'str' keys, got 'IntKeys'
          f(**{1: 2}) # WARNING Expected a mapping with 'str' keys, got 'dict[int, int]'
          f(**tokens) # WARNING Expected a mapping with 'str' keys, got 'dict[Token, int]'
          f(**colors) # WARNING Expected a mapping with 'str' keys, got 'dict[Color, int]'
      """.trimIndent())

    @Test
    fun `key type that can hold str keys is not reported`() = test("""
      from collections.abc import Hashable
      from enum import StrEnum
      from typing import Any, SupportsIndex, TypedDict

      class Name(str): ...

      class Key: ...

      class Field(StrEnum):
          NAME = "name"

      class Options(TypedDict):
          verbose: bool

      def f(**kwargs: object): ...

      def g[K](strs: dict[str, int], names: dict[Name, int], objects: dict[object, int], mixed: dict[str | int, int],
               hashables: dict[Hashable, int], anything: dict[Any, int], generic: dict[K, int], options: Options,
               keys: dict[Key, int], indexes: dict[SupportsIndex, int], fields: dict[Field, int]):
          f(**keys)
          f(**indexes)
          f(**fields)
          f(**strs)
          f(**names)
          f(**objects)
          f(**mixed)
          f(**hashables)
          f(**anything)
          f(**generic)
          f(**options)
          f(**{"a": 1})
      """.trimIndent())

    @Test
    fun `dict display accepts any key type`() = test("""
      class IntKeys:
          def keys(self) -> list[int]: ...
          def __getitem__(self, key: int) -> int: ...

      class KeysOnly:
          def keys(self) -> list[str]: ...

      def g(ints: dict[int, int], int_keys: IntKeys, keys_only: KeysOnly, pairs: list[tuple[int, int]]):
          expr = {**ints, **int_keys, **{1: 2}}
          expr = {**keys_only} # WARNING Expected a mapping, got 'KeysOnly'
          expr = {**pairs} # WARNING Expected a mapping, got 'list[tuple[int, int]]'
          expr = {**None} # WARNING Expected a mapping, got 'None'
      """.trimIndent())
  }

  @Nested
  inner class RepeatedKeyword {
    @Test
    fun `keyword given by an explicit argument and a mapping`() = test("""
      def f(a=0, c=0, **kwargs): ...

      f(c=1, **{"c": 2}) # WARNING Multiple values for argument 'c'
      f(**{"c": 2}, c=1) # WARNING Multiple values for argument 'c'
      f(d=1, **{"d": 2}) # WARNING Multiple values for argument 'd'
      f(c=1, **{"d": 2})
      """.trimIndent())

    @Test
    fun `keyword given by two mappings`() = test("""
      def f(a=0, c=0, **kwargs): ...

      f(**{"c": 1}, **{"c": 2}) # WARNING Multiple values for argument 'c'
      f(**{"c": 1}, **{"d": 2})
      f(**{"c": 1, **{"d": 2}}, **{"d": 3}) # WARNING Multiple values for argument 'd'
      """.trimIndent())

    @Test
    fun `one problem is reported once`() = test("""
      def h(a, **kwargs): ...

      h(1, **{"a": 2}, **{"a": 3})
      #    │           ^^^^^^^^^^ WARNING Multiple values for argument 'a'
      #    ^^^^^^^^^^ WARNING Multiple values for argument 'a'
      h(1, a=2, **{"a": 3})
      #         ^^^^^^^^^^ WARNING Multiple values for argument 'a'
      """.trimIndent())

    @Test
    fun `keyword given by a required TypedDict key`() = test("""
      from typing import NotRequired, TypedDict

      class Required(TypedDict):
          c: int

      class Optional(TypedDict):
          c: NotRequired[int]

      def f(a=0, c=0, **kwargs): ...

      def g(required: Required, optional: Optional):
          f(c=1, **required) # WARNING Multiple values for argument 'c'
          f(c=1, **optional)
      """.trimIndent())

    @Test
    fun `keyword of a mapping with unknown keys is not reported`() = test("""
      def f(a=0, c=0, **kwargs): ...

      def g(strs: dict[str, int]):
          f(c=1, **strs)
          f(**strs, **strs)
          f(**{key: 1 for key in "c"}, c=2)
      """.trimIndent())

    @Test
    fun `mapping key for a parameter that a positional argument filled`() = test("""
      def f(a, b): ...
      def positional_only(a, /, **kwargs): ...

      def g(strs: dict[str, int]):
          f(1, **{"a": 2}) # WARNING Multiple values for argument 'a'
          f(*[1], **{"a": 2}) # WARNING Multiple values for argument 'a'
          f(1, **{"b": 2})
          f(1, **strs)
          positional_only(1, **{"a": 2})
      """.trimIndent())
  }

  @Nested
  inner class DoubleStarParameters {
    @Test
    fun `mapping cannot fill a positional-only parameter`() = test("""
      def f(a, b, /): ...

      def g(strs: dict[str, int]):
          f(**strs)
      #           └ WARNING Parameter 'a' unfilled
      #           └ WARNING Parameter 'b' unfilled
      """.trimIndent())

    @Test
    fun `mapping fills keyword-only parameters`() = test("""
      def f(*, a, b): ...

      def g(strs: dict[str, int]):
          f(**{"a": 1, "b": 2})
          f(**strs)
      """.trimIndent())

    @Test
    fun `mapping that can be empty fills nothing`() = test("""
      from typing import NotRequired, TypedDict

      class Optional(TypedDict):
          z: NotRequired[int]

      class Required(TypedDict):
          z: int

      def f(a, b): ...

      def g(strs: dict[str, int], optional: Optional, required: Required):
          f(1, 2, **{})
          f(1, 2, **strs)
          f(1, 2, **optional)
          f(1, 2, **{"z": 1}) # WARNING Unexpected argument
          f(1, 2, **required) # WARNING Unexpected argument
      """.trimIndent())
  }

  @Nested
  inner class StarOperand {
    @Test
    fun `operand must be iterable`() = test("""
      def f(*args): ...

      def g(number: int):
          f(*number) # WARNING Expected an iterable, got 'int'
          f(*None) # WARNING Expected an iterable, got 'None'
      """.trimIndent())

    @Test
    fun `iterable operands are accepted`() = test("""
      class LegacyIterable:
          def __getitem__(self, index: int) -> int: ...

      def f(*args): ...

      f(*"ab")
      f(*{"x": 1, "y": 2})
      f(*LegacyIterable())
      f(*range(2))
      """.trimIndent())

    @Test
    fun `operand cannot fill keyword-only parameters`() = test("""
      def f(*, a, b): ...

      def g(items: list[int]):
          f(*items)
      #           └ WARNING Parameter 'a' unfilled
      #           └ WARNING Parameter 'b' unfilled
      """.trimIndent())

    @Test
    fun `operand fills positional-only parameters`() = test("""
      def f(a, b, /): ...

      def g(items: list[int], pair: tuple[int, str]):
          f(*items)
          f(*pair)
      """.trimIndent())

    @Test
    fun `operand after a keyword argument fills the leading parameters`() = test("""
      def f(a, b): ...

      def g(items: list[int]):
          f(b=1, *[2])
          f(b=1, *items)
      """.trimIndent())

    @Test
    fun `several star and double star arguments in one call`() = test("""
      def f(a, b=0, *args, c=0, **kwargs): ...

      def g(items: list[int], strs: dict[str, int]):
          f(*[1], *items, **{"c": 4}, **strs)
      """.trimIndent())

    @Test
    fun `operand of known length`() = test("""
      def f(a, b): ...

      def g(one: tuple[int], two: tuple[int, str], three: tuple[int, int, int]):
          f(*two)
          f(*three) # WARNING Unexpected argument
          f(1, *two) # WARNING Unexpected argument
          f(1, 2, *two) # WARNING Unexpected argument
          f(*one)
      #         └ WARNING Parameter 'b' unfilled
          f(*two, b=1) # WARNING Unexpected argument
      """.trimIndent())

    @Test
    fun `operand of known length is accepted by star args`() = test("""
      def f(a, *args): ...

      def g(three: tuple[int, int, int]):
          f(*three)
          f(1, *three)
      """.trimIndent())

    @Test
    fun `operand of unknown length is not counted`() = test("""
      def f(a, b): ...

      def g(items: list[int], rest: tuple[int, ...]):
          f(*items)
          f(1, *items)
          f(1, 2, *items)
          f(1, 2, *rest)
          f(1, 2, *[])
          f(1, 2, *[*items])
      """.trimIndent())

    @Test
    fun `operand with a part of unknown length is not counted`() = test("""
      def one(a): ...
      def two(a, b): ...
      def three(a, b, c): ...
      def two_default(a, b=0): ...

      def g[*Ts](open_tail: tuple[int, *tuple[str, ...]], items: list[int], *args: *Ts):
          one(*open_tail)
          two(1, *open_tail)
          three(*open_tail)
          one(1, *args)
          two(*args)
          three(1, 2, *[*items, 3])
          two(*[*items])
          two_default(*[*items], **{"b": 1})
      """.trimIndent())

    @Test
    fun `parameter after a known spread can come from a mapping`() = test("""
      def two(a, b): ...

      def g(one: tuple[int], strs: dict[str, int]):
          two(*one, **strs)
      """.trimIndent())

    @Test
    fun `operand that can be empty matches an overload`() = test("""
      from typing import overload

      @overload
      def f(a: int, b: int) -> int: ...
      @overload
      def f(a: str, b: str) -> str: ...
      def f(a, b): ...

      def g(strs: dict[str, int], items: list[int]):
          f(1, 2, **strs)
          f(1, 2, *items)
      """.trimIndent())
  }

  @Nested
  inner class Displays {
    @Test
    fun `star in list, tuple and set displays must be iterable`() = test("""
      class LegacyIterable:
          def __getitem__(self, index: int) -> int: ...

      def g(number: int, items: list[int]):
          expr = [*number] # WARNING Expected an iterable, got 'int'
          expr = (*None,) # WARNING Expected an iterable, got 'None'
          expr = {*number} # WARNING Expected an iterable, got 'int'
          expr = [*items, *"ab", *LegacyIterable()]
          expr = {*LegacyIterable()}
      """.trimIndent())
  }
}
