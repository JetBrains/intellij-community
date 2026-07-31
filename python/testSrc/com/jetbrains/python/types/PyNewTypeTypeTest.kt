// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.types

import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Components
import com.intellij.idea.TestFor
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import org.junit.jupiter.api.Test

/**
 * Type and type-checker tests for [typing.NewType](https://docs.python.org/3/library/typing.html#newtype).
 */
@Subsystems.Typing
@Components.TypeInference
@Layers.Functional
class PyNewTypeTypeTest : PyCodeInsightTestCase() {

  @Test
  @TestFor(issues = ["PY-21302"])
  fun `NewType instance`() = test("""
      from typing import NewType
      UserId = NewType('UserId', int)
      expr = UserId(12)
      # └ TYPE UserId
      """)

  @Test
  @TestFor(issues = ["PY-21302"])
  fun `NewType instance with keyword arguments`() = test("""
      from typing import NewType
      UserId = NewType(tp=int, name='UserId')
      expr = UserId(12)
      #└ TYPE UserId
      """)

  @Test
  @TestFor(issues = ["PY-21302"])
  fun `NewType factory of generic`() = test("""
      from typing import Dict, NewType
      UserId = NewType('UserId', Dict[int, str])
      expr = UserId
      #└ TYPE (dict[int, str]) -> UserId
      """)

  @Test
  @TestFor(issues = ["PY-21302"])
  fun `NewType as parameter type`() = test("""
      from typing import Dict, NewType
      UserId = NewType('UserId', int)
      def foo(a: UserId) -> str
      #                        └ ERROR ':' expected
          pass
      expr = foo
      #└ TYPE (a: UserId) -> str
      """)

  @Test
  @TestFor(issues = ["PY-21302"])
  fun `NewType imported aliased`() = test("""
      from typing import NewType as nt
      UserId = nt('UserId', int)
      expr = UserId(12)
      #└ TYPE UserId
      """)

  @Test
  @TestFor(issues = ["PY-21302"])
  fun `NewType via module qualifier`() = test("""
      import typing
      UserId = typing.NewType('UserId', int)
      expr = UserId(12)
      #└ TYPE UserId
      """)

  @Test
  @TestFor(issues = ["PY-21302"])
  fun `NewType via aliased module qualifier`() = test("""
      import typing as t
      UserId = t.NewType('UserId', int)
      expr = UserId(12)
      #└ TYPE UserId
      """)

  @Test
  @TestFor(issues = ["PY-21302"])
  fun `NewType nested over NewType`() = test("""
      from typing import NewType
      UserId = NewType('UserId', int)
      SuperId = NewType('SuperId', UserId)
      expr = SuperId(UserId(12))
      #└ TYPE SuperId
      """)

  @Test
  fun `NewType factory type`() = test("""
      from typing import NewType
      UserId = NewType('UserId', int)
      expr = UserId
      #└ TYPE (int) -> UserId
      """)

  @Test
  @TestFor(issues = ["PY-21302"])
  fun `initializing NewType`() = test("""
      from typing import NewType, Dict

      UserId = NewType("UserId", int)

      a = UserId(42)
      b = UserId("John") # WARNING Expected type 'int', got 'Literal["John"]' instead

      KeyValue = NewType("KeyValue", Dict[str, int])

      KeyValue({"key": 13})
      KeyValue(42) # WARNING Expected type 'dict[str, int]', got 'Literal[42]' instead
      KeyValue({"key1": "key2"}) # WARNING Expected type 'dict[str, int]', got 'dict[Literal["key1"], Literal["key2"]]' instead
      """)

  @Test
  @TestFor(issues = ["PY-21302"])
  fun `NewType as parameter type-checked`() = test("""
      from typing import NewType

      UserId = NewType("UserId", int)

      def get_user(user: UserId) -> str:
          pass


      get_user(UserId(5))
      get_user("John") # WARNING Expected type 'UserId', got 'Literal["John"]' instead
      get_user(4) # WARNING Expected type 'UserId', got 'Literal[4]' instead
      """)

  @Test
  @TestFor(issues = ["PY-21302"])
  fun `NewType inheritance type-checked`() = test("""
      from typing import NewType

      UserId = NewType("UserId", int)
      NewId = NewType("NewId", UserId)
      ChildNewId = NewType("ChildNewId", NewId)

      def get_user_super(user: UserId) -> str:
          pass

      def get_user_child(user: NewId) -> str:
          pass

      def get_user_child_new(user: ChildNewId):
          pass


      user = UserId(12)
      new_id = NewId(user)
      child_new_id = ChildNewId(new_id)

      get_user_super(user)
      get_user_super(new_id)
      get_user_super(child_new_id)

      get_user_child(user) # WARNING Expected type 'NewId', got 'UserId' instead
      get_user_child(new_id)
      get_user_child(child_new_id)

      get_user_child_new(user) # WARNING Expected type 'ChildNewId', got 'UserId' instead
      get_user_child_new(new_id) # WARNING Expected type 'ChildNewId', got 'NewId' instead
      get_user_child_new(child_new_id)
      """)
}
