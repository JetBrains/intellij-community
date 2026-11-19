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
}
