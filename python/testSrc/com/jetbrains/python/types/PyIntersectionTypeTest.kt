// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.types

import com.intellij.idea.TestFor
import com.jetbrains.python.allure.Components
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Type tests for the `ty_extensions.Intersection` form, and for operations whose subject is an
 * intersection type: attribute, property and descriptor access, and calls.
 * [PySubtypingTypeTest] covers the assignability of an intersection.
 */
@Subsystems.Typing
@Components.TypeInference
@Layers.Functional
class PyIntersectionTypeTest : PyCodeInsightTestCase() {

  /**
   * `ty_extensions` is an SDK root here, so [TestCaseOptions.assertSdkRootsNotParsed] also holds that the
   * form resolves from the stub, without a parse of `ty_extensions.pyi`.
   */
  @Nested
  inner class TyExtensionsForm {

    @Test
    @TestFor(issues = ["PY-87024"])
    @TestCaseOptions(additionalSdkRoots = [SdkRoot(TY_EXTENSIONS_ROOT, OrderRootTypeEnum.CLASSES)])
    fun `Intersection of two members`() = test("""
      from ty_extensions import Intersection

      expr: Intersection[int, str]
      # └ TYPE int & str
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-87024"])
    @TestCaseOptions(additionalSdkRoots = [SdkRoot(TY_EXTENSIONS_ROOT, OrderRootTypeEnum.CLASSES)])
    fun `Intersection of one member collapses to that member`() = test("""
      from ty_extensions import Intersection

      expr: Intersection[int]
      # └ TYPE int
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-87024"])
    @TestCaseOptions(additionalSdkRoots = [SdkRoot(TY_EXTENSIONS_ROOT, OrderRootTypeEnum.CLASSES)])
    fun `Intersection of no members collapses to the top type`() = test("""
      from ty_extensions import Intersection

      expr: Intersection[()]
      # └ TYPE object
      """.trimIndent())
  }

  @Nested
  inner class MemberAccess {

    @Test
    @TestFor(issues = ["PY-87028"])
    fun `attribute declared in several members intersects the declarations`() = test("""
      class C: pass
      class D: pass

      class A:
          cd: C
      class B:
          cd: D

      def f(ab: A):
          if isinstance(ab, B):
              expr = ab.cd
      #       └ TYPE C & D
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-87028"])
    fun `attribute declared in one member keeps that declaration`() = test("""
      class C: pass
      class D: pass

      class A:
          cd: C
      class B:
          other: D

      def f(ab: A):
          if isinstance(ab, B):
              expr = ab.cd
      #       └ TYPE C
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-89000"])
    fun `property on a member`() = test("""
      class A: pass
      class B:
          @property
          def foo(self) -> int: ...

      def f(ab: A):
          if isinstance(ab, B):
              expr = ab.foo
      #       └ TYPE int
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-89000"])
    fun `descriptor on a member`() = test("""
      class Desc:
          def __get__(self, obj, owner) -> int: ...

      class A: pass
      class B:
          foo = Desc()

      def f(ab: A):
          if isinstance(ab, B):
              expr = ab.foo
      #       └ TYPE int
      """.trimIndent())

    /**
     * A member without the attribute declares nothing, so it must not widen the result to Unknown.
     */
    @Test
    @TestFor(issues = ["PY-87028"])
    fun `member without the attribute drops out`() = test("""
      class A:
          attr: int
      class B: pass

      def f(a: A):
          if isinstance(a, B):
              expr = a.attr
      #       └ TYPE int
      """.trimIndent())

    /**
     * A member that declares the attribute with an unknown type still constrains the result,
     * unlike a member that does not declare it at all.
     */
    @Test
    @TestFor(issues = ["PY-87028"])
    fun `attribute of an unknown type constrains the result`() = test("""
      class A:
          attr: int
      class B:
          attr: not_imported
      #         ^^^^^^^^^^^^ ERROR Unresolved reference 'not_imported'

      def f(a: A):
          if isinstance(a, B):
              expr = a.attr
      #       └ TYPE int & Unknown
      """.trimIndent())

    /**
     * `Any` declares every attribute, so every attribute of `Any & C` keeps an `Any` part.
     */
    @Test
    @TestFor(issues = ["PY-87028"])
    fun `Any member declares every attribute`() = test("""
      from typing import Any

      class Foo:
          foo: str

      def f(x: "Any & Foo"):
          expr = x.foo
      #   └ TYPE Any & str
          deep = x.foo.bar
      #   └ TYPE Any
      """.trimIndent())
  }

  @Nested
  inner class MemberCalls {

    /**
     * The same method in several members forms one callee type, so the call picks the signature that
     * matches the arguments, like an overload set does.
     */
    @Test
    @TestFor(issues = ["PY-89000"])
    fun `call matches the signature of one member`() = test("""
      class A:
          def f(self, a: int) -> int: ...
      class B:
          def f(self, a: str) -> str: ...

      def g(x: A):
          if isinstance(x, B):
              expr = x.f(1)
      #       │          └ WARNING Expected type 'str', got 'Literal[1]' instead FIXME # PY-90282: the argument check does not treat an intersection callee as an overload set yet
      #       └ TYPE int
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-89000"])
    fun `call matches the signature of the other member`() = test("""
      class A:
          def f(self, a: int) -> int: ...
      class B:
          def f(self, a: str) -> str: ...

      def g(x: A):
          if isinstance(x, B):
              expr = x.f("s")
      #       │          ^^^ WARNING Expected type 'int', got 'Literal["s"]' instead FIXME # PY-90282: the argument check does not treat an intersection callee as an overload set yet
      #       └ TYPE str
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-90282"])
    fun `overloaded method on a member`() = test("""
      from typing import overload

      class A: pass
      class B:
          @overload
          def m(self, x: int) -> int: ...
          @overload
          def m(self, x: str) -> str: ...
          def m(self, x): return x

      def f(ab: A):
          if isinstance(ab, B):
              expr = ab.m(1)
      #       └ TYPE int
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-90282"])
    fun `calling an intersection uses the callable member`() = test("""
      class A: pass
      class B:
          def __call__(self) -> int: return 1

      def f(ab: A):
          if isinstance(ab, B):
              expr = ab()
      #       └ TYPE int
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-89000"])
    fun `a callable member makes the intersection callable`() = test("""
      class A: pass
      class B:
          def __call__(self) -> int: return 1

      def f(ab: A):
          if isinstance(ab, B):
              ab()
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-89000"])
    fun `an intersection without a callable member is reported`() = test("""
      class A: pass
      class B: pass

      def f(ab: A):
          if isinstance(ab, B):
              ab() # WARNING 'ab' is not callable
      """.trimIndent())
  }

  companion object {
    private const val TY_EXTENSIONS_ROOT = "types/tyExtensions"
  }
}
