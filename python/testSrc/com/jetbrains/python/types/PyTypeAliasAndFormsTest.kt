// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.types

import com.intellij.idea.TestFor
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import com.jetbrains.python.psi.LanguageLevel
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Type and type-checker tests for type-alias forms (`TypeAlias`, `type X = ...`), the `Self` type,
 * `LiteralString`, forward references and quoted (string) type annotations, `sys.version_info` and
 * `TYPE_CHECKING` version guards, walrus/assignment expressions, `super()` typing, module/import
 * attribute types and the `Final`/`ClassVar`/`type[...]` type forms.
 */
class PyTypeAliasAndFormsTest : PyCodeInsightTestCase() {

  override val defaultTestOptions = TestOptions(enablePyAnyType = false)

  @Nested
  inner class TypeObjectForms {
    @Test
    @TestFor(issues = ["PY-7058"])
    fun `type of an instance is a class object type`() = test(
      """
      class C:
          pass

      x = C()
      expr = type(x)
      # └ TYPE type[C]
      """,
    )

    @Test
    @TestFor(issues = ["PY-7058"])
    fun `type of a class object is type`() = test(
      """
      class C:
          pass

      expr = type(C)
      # └ TYPE type
      """,
    )

    @Test
    @TestFor(issues = ["PY-7058"])
    fun `type of an unknown value is Any`() = test(
      """
      def f(x):
          expr = type(x)
      #   └ TYPE Any
      """,
    )

    @Test
    fun `Type of union of class objects`() = test("""
      from typing import Type, Union

      def f(x: Type[Union[int, str]]):
          expr = x
      #   └ TYPE type[int | str]
      """)

    @Test
    fun `type of Self in classmethod`() = test("""
      from typing import Self
      class Test:
          @classmethod
          def foo(cls) -> type[Self]:
              return cls

      expr = Test.foo()
      # └ TYPE type[Test]
      """)

    @Test
    @TestFor(issues = ["PY-20057"])
    fun `type and class object types compatibility`() = test("""
      from typing import TypeVar, Type

      T = TypeVar('T')
      S = TypeVar('T', str) # ERROR A single constraint is not allowed


      def expects_type(x: type):
          pass


      def expects_typing_type(x: Type):
          expects_type(x)


      def expects_typing_type_any(x: Type[Any]): # ERROR Unresolved reference 'Any'
          expects_type(x)


      def expects_any_type_via_type_var(x: Type[T]):
          expects_type(x)


      def expects_str_class(x: Type[str]):
          expects_type(x)


      def expects_str_subclass(x: Type[S]):
          expects_type(x)


      def expects_object(x: object):
          expects_type(x) # WARNING Expected type 'type', got 'object' instead


      expects_type(type)
      expects_type
      expects_typing_type(type)
      expects_typing_type_any(type)
      expects_typing_type
      expects_str_class(type) # WARNING Expected type 'type[str]', got 'type[type]' instead
      expects_any_type_via_type_var(type)
      expects_str_subclass(type) # WARNING Expected type 'type[T ≤: str]', got 'type[type]' instead
      expects_object(type)
      """)

    @Test
    @TestFor(issues = ["PY-20057"])
    fun `class object type with union`() = test("""
      from typing import Type, Union

      class MyClass:
          pass

      def expects_myclass_or_str1(x: Type[Union[MyClass, str]]):
          pass

      expects_myclass_or_str1(MyClass)
      expects_myclass_or_str1(str)
      expects_myclass_or_str1(int) # WARNING Expected type 'type[MyClass | str]', got 'type[int]' instead
      expects_myclass_or_str1(42) # WARNING Expected type 'type[MyClass | str]', got 'Literal[42]' instead


      def expects_myclass_or_str2(x: Union[Type[MyClass], Type[str]]):
          pass

      expects_myclass_or_str2(MyClass)
      expects_myclass_or_str2(str)
      expects_myclass_or_str2(int) # WARNING Expected type 'type[MyClass | str]', got 'type[int]' instead
      expects_myclass_or_str2(42) # WARNING Expected type 'type[MyClass | str]', got 'Literal[42]' instead
      """)

    @Test
    @TestFor(issues = ["PY-43838"])
    fun `parameterized class against Type`() = test("""
      from typing import Type, Any, List

      def my_function(param: Type[Any]):
          pass

      my_function(List[str])
      """)

    @Test
    @TestFor(issues = ["PY-43838"])
    fun `union against Type`() = test("""
      from typing import Type, Any, Union

      def my_function(param: Type[Any]):
          pass

      my_function(Union[int, str])
      """)
  }

  @Nested
  inner class FinalAndClassVarForms {
    @Test
    @TestFor(issues = ["PY-34945"])
    fun `Final with explicit type`() = test("""
      from typing_extensions import Final
      expr: Final[int] = undefined # ERROR Unresolved reference 'undefined'
      #└ TYPE int
      """)

    @Test
    @TestFor(issues = ["PY-34945"])
    fun `Final inferred from literal`() = test("""
      from typing_extensions import Final
      expr: Final = 5
      #└ TYPE Literal[5]
      """)

    @Test
    @TestFor(issues = ["PY-34945"])
    fun `Final without value`() = test("""
      from typing_extensions import Final
      expr: Final[int] # WARNING 'Final' name should be initialized with a value
      #└ TYPE int
      """)

    @Test
    @TestFor(issues = ["PY-34945"])
    fun `Final inferred from list literal`() = test("""
      from typing_extensions import Final
      expr: Final = [1, 2]
      #└ TYPE list[int]
      """)

    @Test
    @TestFor(issues = ["PY-34945"])
    fun `Final in type comment with explicit type`() = test(
      """
      from typing_extensions import Final
      expr = undefined  # type: Final[int]
      #│     ^^^^^^^^^ ERROR Unresolved reference 'undefined'
      #└ TYPE int
      """,
    )

    @Test
    @TestFor(issues = ["PY-34945"])
    fun `Final in type comment inferred from literal`() = test(
      """
      from typing_extensions import Final
      expr = 5  # type: Final
      #└ TYPE Literal[5]
      """,
    )

    @Test
    fun `ClassVar type resolved from annotation`() = test("""
      from typing import ClassVar
      class A:
          x: ClassVar[int] = 1
      expr = A.x
      #└ TYPE int
      """)

    @Test
    fun `ClassVar type resolved from type comment`() = test("""
      from typing import ClassVar
      class A:
          x = 1  # type: ClassVar[int]
      expr = A.x
      #└ TYPE int
      """)
  }

  @Nested
  inner class ExplicitTypeAliases {
    @Test
    fun `local type alias`() = test("""
      def func(g):
          Alias = int
          expr: Alias = g()
      #   └ TYPE int
      """)

    @Test
    @TestFor(issues = ["PY-18816"])
    fun `local type alias in function type comment`() = test("""
      def func():
          Alias = int
          def g(x):
              # type: (Alias) -> None
              expr = x
      #       └ TYPE int
      """)

    @Test
    fun `explicit TypeAlias annotation yields class object type`() = test("""
      from typing import TypeAlias

      expr: TypeAlias = int
      #└ TYPE type[int]
      """)

    @Test
    @TestFor(issues = ["PY-89068"])
    fun `imported generic explicit TypeAlias type`() = test(
      """
      from m import MyList

      expr = MyList[int]
      #└ TYPE type[list[int]]
      """,
      "m.py" to """
        from typing import TypeAlias, TypeVar

        T = TypeVar("T")

        MyList: TypeAlias = list[T]
        """,
    )

    @Test
    fun `TypeAlias to Any`() = test("""
      from typing import Any, TypeAlias

      Plug: TypeAlias = Any
      expr: int | Plug
      #└ TYPE int | Any
      """)

    @Test
    fun `new-style type alias to Any`() = test("""
      from typing import Any

      type Plug = Any
      expr: int | Plug
      #└ TYPE int | Any
      """)

    @Test
    fun `type alias statement type not interpreted as assigned type outside of type hint`() = test("""
      type myType = str
      expr = myType
      #└ TYPE TypeAliasType
      """)

    @Test
    @TestFor(issues = ["PY-51329"])
    fun `bitwise or operator overload union via type alias`() = test("""
      from typing import Any

      class MyMeta(type):
          def __or__(self, other) -> Any:
              return other

      class Foo(metaclass=MyMeta):
          ...

      Alias = Foo | None
      expr: Alias # WARNING Invalid type annotation
      #└ TYPE Any
      """)
  }

  @Nested
  inner class ForwardReferencesAndQuotedAnnotations {
    @Test
    fun `type in string literal`() = test("""
      class C:
          def foo(self, expr: 'C'):
      #                  └ TYPE C
              pass
      """)

    @Test
    fun `qualified type in string literal`() = test(TestOptions(assertRecursionPrevention = false), """
      import typing

      def foo(x: 'typing.AnyStr') -> typing.AnyStr:
          pass

      expr = foo('bar')
      #└ TYPE str
      """)

    @Test
    fun `quoted forward reference in type comment`() = test(
      TestOptions(languageLevel = LanguageLevel.PYTHON27, enablePyAnyType = false),
      """
      def foo(x):
          # type: (MyClass) -> None
          expr = x
      #   └ TYPE MyClass

      class MyClass: ... # ERROR Python version 2.7 does not support '...' outside of sequence slicings
      """,
    )

    @Test
    @TestFor(issues = ["PY-86223"])
    fun `quoted type parameter in type hint`() = test("""
      def foo[T](p: "T"):
          expr = p
      #   └ TYPE T
      """)

    @Test
    @TestFor(issues = ["PY-86223"])
    fun `generic type with quoted type parameter in type hint`() = test("""
      def foo[T](p: list["T"]):
          expr = p
      #   └ TYPE list[T]
      """)

    @Test
    @TestFor(issues = ["PY-86223"])
    fun `quoted generic type with type parameter in type hint`() = test("""
      def foo[T](p: "list[T]"):
          expr = p
      #   └ TYPE list[T]
      """)

    @Test
    @TestFor(issues = ["PY-86223"])
    fun `quoted reference to local class in type hint`() = test("""
      def outer():
          class A: ...

          def inner(a: "A", b: "B"):
              expr = (a, b)
      #       └ TYPE tuple[A, B]

          class B: ...
      """)

    @Test
    fun `quoted forward reference in type hint`() = test("""
      def foo(x: "MyClass"):
          expr = x
      #   └ TYPE MyClass

      class MyClass: ...
      """)

    @Test
    fun `generic type with quoted forward reference in type hint`() = test("""
      def foo(x: list["MyClass"]):
          expr = x
      #   └ TYPE list[MyClass]

      class MyClass: ...
      """)

    @Test
    fun `quoted generic type with forward reference in type hint`() = test("""
      def foo(x: "list[MyClass]"):
          expr = x
      #   └ TYPE list[MyClass]

      class MyClass: ...
      """)

    @Test
    @TestFor(issues = ["PY-84430"])
    fun `quoted Any`() = test("""
      from typing import Any

      any: "Any" = 1

      expr = any.imag
      #└ TYPE Literal[0] FIXME Any
      """)
  }

  @Nested
  inner class LiteralStrings {
    @Test
    @TestFor(issues = ["PY-53612"])
    fun `LiteralString valid locations`() = test("""
      from typing_extensions import LiteralString
      def my_function(literal_string: LiteralString) -> LiteralString: ...
      expr = my_function("42")
      #└ TYPE LiteralString
      """)

    @Test
    @TestFor(issues = ["PY-53612"])
    fun `LiteralString concatenation of two literal strings`() = test("""
      from typing_extensions import LiteralString
      x: LiteralString
      y: LiteralString
      expr = x + y
      #└ TYPE LiteralString
      """)

    @Test
    @TestFor(issues = ["PY-53612"])
    fun `LiteralString concatenation with str on the right`() = test("""
      from typing_extensions import LiteralString
      x: LiteralString
      y: str
      expr = x + y
      #└ TYPE str
      """)

    @Test
    @TestFor(issues = ["PY-53612"])
    fun `LiteralString concatenation with str on the left`() = test("""
      from typing_extensions import LiteralString
      x: str
      y: LiteralString
      expr = x + y
      #└ TYPE str
      """)

    @Test
    @TestFor(issues = ["PY-53612"])
    fun `LiteralString join of literal strings`() = test("""
      from typing_extensions import LiteralString
      x: LiteralString
      xs: list[LiteralString]
      expr = x.join(xs)
      #└ TYPE LiteralString
      """)

    @Test
    @TestFor(issues = ["PY-53612"])
    fun `LiteralString join on plain str receiver`() = test("""
      from typing_extensions import LiteralString
      x: str
      xs: list[LiteralString]
      expr = x.join(xs)
      #└ TYPE str
      """)

    @Test
    @TestFor(issues = ["PY-53612"])
    fun `LiteralString join with str elements`() = test("""
      from typing_extensions import LiteralString
      x: LiteralString
      xs: list[str]
      expr = x.join(xs)
      #└ TYPE str
      """)

    @Test
    @TestFor(issues = ["PY-53612"])
    fun `LiteralString format with literal arguments`() = test("""
      from typing_extensions import LiteralString
      name: LiteralString = "foo"
      age: LiteralString = "42"
      string: LiteralString = "Hello, {name}. You are {age}"
      expr = string.format(name=name.capitalize(), age=age)
      # └ TYPE LiteralString
      """)

    @Test
    @TestFor(issues = ["PY-53612"])
    fun `LiteralString format with str argument`() = test(TestOptions(assertRecursionPrevention = false), """
      from typing_extensions import LiteralString
      name: LiteralString = "foo"
      age = str(42)
      string: LiteralString = "Hello, {name}. You are {age}"
      expr = string.format(name=name.capitalize(), age=age)
      #│     │                  ^^^^^^^^^^^^^^^^^ WARNING 'capitalize' is not callable
      #│     ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^ WARNING 'format' is not callable
      #└ TYPE Unknown
      """)

    @Test
    fun `LiteralString is not inferred without explicit annotation for list literal addition`() = test("""
      expr = ['1' + '2']
      #└ TYPE list[str]
      """)

    @Test
    fun `LiteralString is not inferred without explicit annotation for same-type call`() = test("""
      from typing import TypeVar
      T = TypeVar("T")
      def same_type(x: T, y: T) -> T:
          pass
      s: str
      expr = same_type(['foo'], [s])
      #└ TYPE list[str]
      """)

    @Test
    fun `LiteralString is not inferred without explicit annotation for list of strings`() = test("""
      expr = ['foo', 'bar']
      #└ TYPE list[str]
      """)

    @Test
    fun `LiteralString is not inferred without explicit annotation for deque of strings`() = test("""
      from collections import deque
      expr = deque(['foo', 'bar'])
      #└ TYPE deque[str]
      """)

    @Test
    fun `LiteralString inferred for string addition`() = test("""
      expr = '1' + '2'
      #└ TYPE LiteralString
      """)

    @Test
    fun `LiteralString inferred for percent format of literal`() = test("""
      expr = '%s' % ('a')
      #└ TYPE LiteralString
      """)

    @Test
    @TestFor(issues = ["PY-53612"])
    fun `LiteralString in place of str inspection`() = test("""
      from typing import LiteralString

      def expectsStr(x: str):
          expectsLiteralString(x) # WARNING Expected type 'LiteralString', got 'str' instead


      def expectsLiteralString(x: LiteralString):
          expectsStr(x)
      """)

    @Test
    @TestFor(issues = ["PY-53612"])
    fun `LiteralString equals to str inspection`() = test("""
      from typing_extensions import LiteralString
      s: str
      literal_string: LiteralString = s # WARNING Expected type 'LiteralString', got 'str' instead
      literal_string: LiteralString = "hello" # WARNING Redeclared 'literal_string' defined above without usage
      """)

    @Test
    @TestFor(issues = ["PY-53612"])
    fun `LiteralString addition inspection`() = test("""
      from typing_extensions import LiteralString
      def expect_literal_string(s: LiteralString) -> None: ...

      expect_literal_string("foo" + "bar")
      literal_string: LiteralString
      expect_literal_string(literal_string + "bar")

      literal_string2: LiteralString
      expect_literal_string(literal_string + literal_string2)

      plain_string: str
      expect_literal_string(literal_string + plain_string) # WARNING Expected type 'LiteralString', got 'str' instead
      expect_literal_string(plain_string + literal_string) # WARNING Expected type 'LiteralString', got 'str' instead
      """)

    @Test
    @TestFor(issues = ["PY-65488"])
    fun `LiteralString implicit concatenation inspection`() = test("""
      from typing_extensions import LiteralString
      def expect_literal_string(s: LiteralString) -> None: ...

      expect_literal_string("foo" "bar")
      expect_literal_string(
        "select "
        "* "
        "from table"
      )

      literal_string: LiteralString = "foo" "bar"
      multiline_literal_string: LiteralString = (
        "select "
        "* "
        "from table"
      )
      """)

    @Test
    @TestFor(issues = ["PY-53612"])
    fun `LiteralString join inspection`() = test("""
      from typing import List
      from typing_extensions import LiteralString
      def expect_literal_string(s: LiteralString) -> None: ...
      expect_literal_string(",".join(["foo", "bar"]))
      literal_string: LiteralString
      expect_literal_string(literal_string.join(["foo", "bar"]))
      literal_string2: LiteralString
      expect_literal_string(literal_string.join([literal_string, literal_string2]))

      xs: List[LiteralString]
      expect_literal_string(literal_string.join(xs))
      plain_string: str
      expect_literal_string(plain_string.join([literal_string, literal_string2])) # WARNING Expected type 'LiteralString', got 'str' instead
      expect_literal_string(literal_string.join([plain_string, literal_string2])) # WARNING Expected type 'LiteralString', got 'str' instead
      """)

    @Test
    @TestFor(issues = ["PY-53612"])
    fun `Literal in place of LiteralString`() = test("""
      from typing import LiteralString, Literal
      def literal_identity(s: LiteralString) -> LiteralString:
          return s
      hello: Literal["hello"] = "hello"
      literal_identity(hello)
      """)

    @Test
    @TestFor(issues = ["PY-53612"])
    fun `str in place of LiteralString with f-string`() = test("""
      from typing import LiteralString
      def expect_literal_string(s: LiteralString) -> None: ...
      plain_string: str
      literal_string: LiteralString
      expect_literal_string(f"hello {literal_string}")
      expect_literal_string(f"hello {plain_string}") # WARNING Expected type 'LiteralString', got 'str' instead
      """)

    @Test
    @TestFor(issues = ["PY-53612"])
    fun `generic substitution with LiteralString`() = test("""
      from typing import TypeVar, LiteralString
      T = TypeVar('T')
      def calc(a: T, b: T):
          pass
      plain_string: str
      literal_string: LiteralString
      calc('literal string', plain_string)
      calc(literal_string, plain_string)
      """)

    @Test
    @TestFor(issues = ["PY-61137"])
    fun `LiteralString in conditional statements and expressions`() = test("""
      from typing import LiteralString
      def condition1():
          pass
      def return_literal_string() -> LiteralString:
          return "foo" if condition1() else "bar"  # OK
      def return_literal_str2(literal_string: LiteralString) -> LiteralString:
          return "foo" if condition1() else literal_string  # OK
      """)

    @Test
    @TestFor(issues = ["PY-61137"])
    fun `LiteralString does not get captured inside generics`() =
      test(TestOptions(enablePyAnyType = false, assertRecursionPrevention = false), """
      import typing
      T = typing.TypeVar('T')
      class Box(typing.Generic[T]):
          def __init__(self, x: T) -> None:
              ...
      def same_type(b1: Box[T], b2: Box[T]):
          ...
      b = Box('foo'.upper())
      same_type(b, Box('FOO'))
      """)
  }

  @Nested
  inner class VersionGuards {
    @Test
    @TestFor(issues = ["PY-76076"])
    fun `function definition under version guard`() = test("""
      import sys
      from typing import TypeVar

      T = TypeVar("T")

      if sys.version_info >= (3,):
          def f(x: T) -> list[T]: ...
      else:
          def f(x: T) -> set[T]: ...

      expr = f("foo")
      #└ TYPE list[str]
      """)

    @Test
    @TestFor(issues = ["PY-76076"])
    fun `class definition under version guard`() = test("""
      import sys
      from typing import TypeVar

      T = TypeVar("T")

      if sys.version_info >= (3,):
          class C:
              def m(self, x: T) -> list[T]: ...
      else:
          class C:
              def m(self, x: T) -> set[T]: ...

      expr = C().m("foo")
      #└ TYPE list[str]
      """)

    @Test
    @TestFor(issues = ["PY-76076"])
    fun `variable definition under version guard`() = test("""
      import sys

      if sys.version_info < (3, 0):
          x: str = "foo"
      else:
          x: int = 42

      expr = x
      #└ TYPE int
      """)

    @Test
    @TestFor(issues = ["PY-77168"])
    fun `referencing imported type from unmatched version guard`() = test("""
      from typing import Literal
      import sys

      if sys.version_info < (3, 0):
          expr: Literal[42]
      #   └ TYPE Literal[42]
      """)

    @Test
    @TestFor(issues = ["PY-77168"])
    fun `referencing top-level type from unmatched version guard`() = test("""
      import sys

      type Alias = int
      if sys.version_info < (3, 0):
          expr: Alias
      #   └ TYPE int
      """)

    @Test
    @TestFor(issues = ["PY-34617"])
    fun `top-level function under version check`() = test(
      TestOptions(languageLevel = LanguageLevel.PYTHON310, enablePyAnyType = false),
      """
      from mod import foo
      expr = foo()
      #└ TYPE str
      """,
      "mod.py" to """
        import sys

        if sys.version_info < (3, 8):
            def foo() -> int:
                pass
        else:
            def foo() -> str:
                pass
        """,
    )

    @Test
    @TestFor(issues = ["PY-34617"])
    fun `class method under version check`() = test(
      TestOptions(languageLevel = LanguageLevel.PYTHON34, enablePyAnyType = false),
      """
      from mod import Foo
      expr = Foo().foo()
      #└ TYPE Union[float, int]
      """,
      "mod.py" to """
        import sys

        if sys.version_info < (2, ):
            pass
        else:
            class Foo:
                if sys.version_info < (3, 2):
                    def foo(self) -> int:
                        pass
                elif sys.version_info < (3, 5):
                    def foo(self) -> float:
                        pass
                else:
                    def foo(self) -> str:
                        pass
        """,
    )

    @Test
    fun `generic alias under version guard`() = test(
      """
      from mod import f

      expr = f("foo")
      #└ TYPE list[str]
      """,
      "mod.py" to """
        import sys
        from typing import TypeAlias, TypeVar

        T = TypeVar("T")

        if sys.version_info >= (3,):
            Alias: TypeAlias = list[T]
        else:
            Alias: TypeAlias = set[T]

        def f(x: T) -> Alias[T]:
            pass
        """,
    )

    @Test
    @TestFor(issues = ["PY-76076"])
    fun `generic method return type imported under version guard`() = test(
      """
      from mod import C
      expr = C().m()
      #└ TYPE list[str]
      """,
      "mod.py" to """
        import sys

        if sys.version_info >= (3,):
            from builtins import list as Container
        else:
            from builtins import set as Container


        class C:
            def m(self) -> Container[str]:
                ...
        """,
    )

    @Test
    @TestFor(issues = ["PY-60968"])
    fun `generic method return type imported under version guard in stub`() = test(
      """
      from mod import C
      expr = C().m()
      #└ TYPE list[str]
      """,
      "mod.py" to """
        class C:
            def m(self):
                pass
        """,
      "mod.pyi" to """
        import sys

        if sys.version_info >= (3,):
            from builtins import list as Container
        else:
            from builtins import set as Container


        class C:
            def m(self) -> Container[str]:
                ...
        """,
    )

    @Test
    @TestFor(issues = ["PY-50642"])
    fun `TYPE_CHECKING guard`() = test("""
      from typing import TYPE_CHECKING

      if not not TYPE_CHECKING:
          v: int = -1
      else:
          v: str = 'ab'
      expr = v
      #└ TYPE int
      """)

    @Test
    @TestFor(issues = ["PY-50642"])
    fun `TYPE_CHECKING guard in class body`() = test("""
      from typing import TYPE_CHECKING

      class A:
          if not not TYPE_CHECKING:
              def foo(self) -> int: ...
          else:
              def foo(self) -> str: ...
      expr = A().foo()
      #└ TYPE int
      """)

    @Test
    @TestFor(issues = ["PY-50642"])
    fun `TYPE_CHECKING guard multifile`() = test(
      """
      from mod import v
      expr = v
      #└ TYPE int
      """,
      "mod.py" to """
        import typing

        if not not typing.TYPE_CHECKING:
            v: int = -1
        else:
            v: str = 'ab'
        """,
    )
  }

  @Nested
  inner class WalrusAndSuper {
    @Test
    @TestFor(issues = ["PY-33886"])
    fun `assignment expression in list literal`() = test(
      TestOptions(languageLevel = LanguageLevel.PYTHON38, enablePyAnyType = false),
      """
      [expr := 1]
      # └ TYPE Literal[1]
      """,
    )

    @Test
    @TestFor(issues = ["PY-33886"])
    fun `assignment expression with parenthesized value`() = test(
      TestOptions(languageLevel = LanguageLevel.PYTHON38, enablePyAnyType = false),
      """
      [expr := (1)]
      # └ TYPE Literal[1]
      """,
    )

    @Test
    @TestFor(issues = ["PY-33886"])
    fun `nested assignment expression`() = test(
      TestOptions(languageLevel = LanguageLevel.PYTHON38, enablePyAnyType = false),
      """
      expr = (e := 1)
      #└ TYPE Literal[1]
      """,
    )

    @Test
    @TestFor(issues = ["PY-33886"])
    fun `assignment expression in call argument`() = test(
      TestOptions(languageLevel = LanguageLevel.PYTHON38, enablePyAnyType = false),
      """
      foo(expr := 1)
      #│  └ TYPE Literal[1]
      #^^ ERROR Unresolved reference 'foo'
      """,
    )

    @Test
    @TestFor(issues = ["PY-33886"])
    fun `assignment expression imported member`() = test(
      TestOptions(languageLevel = LanguageLevel.PYTHON38, enablePyAnyType = false),
      """
      from a import member
      expr = member
      #└ TYPE Type[A]
      """,
      "a.py" to """
        class A:
          pass

        (member := A)
        """,
    )

    @Test
    fun `super() with another type`() = test(
      TestOptions(languageLevel = LanguageLevel.PYTHON34, enablePyAnyType = false),
      """
      class A:
          def f(self):
              return 'A'

      class B:
          def f(self):
              return 'B'

      class C(B):
          def f(self):
              return 'C'

      class D(C, A):
          def f(self):
              expr = super(B, self)
      #       └ TYPE A
              return expr.f()
      """,
    )
  }

  @Nested
  inner class SelfType {
    @Test
    @TestFor(issues = ["PY-53104"])
    fun `method returning Self called on subclass instance`() = test("""
      from typing import Self

      class A:
          def foo(self) -> Self:
              ...
      class B(A):
          pass
      expr = B().foo()
      #└ TYPE B
      """)

    @Test
    @TestFor(issues = ["PY-53104"])
    fun `method returning list of Self`() = test("""
      from typing import Self

      class A:
          def foo(self) -> list[Self]:
              ...
      class B(A):
          pass
      expr = B().foo()
      #└ TYPE list[B]
      """)

    @Test
    @TestFor(issues = ["PY-53104"])
    fun `classmethod returning Self`() = test("""
      from typing import Self


      class Shape:
          @classmethod
          def from_config(cls, config: dict[str, float]) -> Self:
              return cls(config["scale"]) # WARNING Unexpected argument


      class Circle(Shape):
          pass


      expr = Circle.from_config({})
      #└ TYPE Circle
      """)

    @Test
    @TestFor(issues = ["PY-53104"])
    fun `classmethod returning Self in nested class`() = test("""
      from typing import Self


      class OuterClass:
          class Shape:
              @classmethod
              def from_config(cls, config: dict[str, float]) -> Self:
                  return cls(config["scale"]) # WARNING Unexpected argument

          class Circle(Shape):
              pass


      expr = OuterClass.Circle.from_config({})
      #└ TYPE Circle
      """)

    @Test
    @TestFor(issues = ["PY-53104"])
    fun `Self method called on union receiver`() = test(
      TestOptions(enablePyAnyType = false, assertRecursionPrevention = false, enableWeakWarnings = false),
      """
      from typing import Self


      class C:
          def method(self) -> Self:
              return self


      if bool():
          x = 42
      else:
          x = C()

      expr = x.method()
      #└ TYPE C
      """,
    )

    @Test
    @TestFor(issues = ["PY-82945"])
    fun `Self substituted with generic qualifier type`() = test("""
      from typing import Self, Generic, TypeVar
      T = TypeVar('T')
      class Base1(Generic[T]):
          def foo(self) -> Self:
              return self

      class Base2:
          def bar(self) -> Self:
              return self

      class Derived(Base1[T], Base2): ...

      d = Derived[int]()
      expr = d.bar().foo().bar().foo()
      #└ TYPE Derived[int]
      """)

    @Test
    @TestFor(issues = ["PY-75679"])
    fun `Self substituted with qualifier type`() = test("""
      from typing import Self

      class A[T]:
          def f(self) -> Self: ...

      class B(A[int]): ...

      expr = B().f()
      #└ TYPE B
      """)

    @Test
    @TestFor(issues = ["PY-88691"])
    fun `Self substituted for classmethod of non-generic derived`() = test("""
      from typing import Self

      class Base[T]:
          @classmethod
          def foo(cls) -> Self:
              return cls()

      class Derived(Base[int]): ...

      expr = Derived.foo()
      #└ TYPE Derived
      """)

    @Test
    @TestFor(issues = ["PY-88691"])
    fun `Self substituted for classmethod of generic derived`() = test("""
      from typing import Self

      class Base[T]:
          @classmethod
          def foo(cls) -> Self:
              return cls()

      class Derived[T](Base[T]): ...

      expr = Derived[int].foo()
      #└ TYPE Derived[int]
      """)

    @Test
    fun `generic class dunder new returns Self`() = test("""
      from typing import Self

      class Box[T]:
          def __new__(cls, parm: T) -> Self:
              ...

      expr = Box("foo")
      #└ TYPE Box[str]
      """)

    @Test
    @TestFor(issues = ["PY-82833"])
    fun `generic class classmethod returning Self called on raw class`() = test("""
      from typing import Self

      class Box[T]:
          @classmethod
          def create(cls, parm: T) -> Self:
              ...

      expr = Box.create("foo")
      #└ TYPE Box[str]
      """)

    @Test
    fun `generic class classmethod returning Self called on instance`() = test("""
      from typing import Self

      class Box[T]:
          @classmethod
          def create(cls, parm: T) -> Self:
              ...

      b: Box[int]
      expr = b.create("foo") # WARNING Expected type 'int' (matched generic type 'T'), got 'Literal["foo"]' instead
      #└ TYPE Any
      """)

    @Test
    fun `generic class method returning Self called on instance`() = test("""
      from typing import Self

      class Box[T]:
          def m(self, parm: T) -> Self:
              ...

      b: Box[int]
      expr = b.m("foo") # WARNING Expected type 'int' (matched generic type 'T'), got 'Literal["foo"]' instead
      #└ TYPE Any
      """)

    @Test
    fun `generic class generic method returning Self called on instance`() = test("""
      from typing import Self

      class Box[T]:
          def m[S](self, parm: S) -> Self:
              ...

      b: Box[int]
      expr = b.m("foo")
      #└ TYPE Box[int]
      """)

    @Test
    @TestFor(issues = ["PY-78044"])
    fun `generator yields Self`() = test("""
      from collections.abc import Generator
      from typing import Self

      class A:
          @classmethod
          def f(cls) -> Generator[Self, None, None]:
              pass

      for x in A.f():
          expr = x
      #   └ TYPE A
      """)

    @Test
    @TestFor(issues = ["PY-78044"])
    fun `generator yields Self in nested hierarchy`() = test("""
      from collections.abc import Generator
      from typing import Self

      class A:
          @classmethod
          def f(cls) -> Generator[Self, None, None]:
              pass

      class B(A): ...
      class C(B): ...

      for x in C.f():
          expr = x
      #   └ TYPE C
      """)

    @Test
    fun `self annotated with type var on same class instance`() = test("""
      from typing import TypeVar

      T = TypeVar('T')

      class C:
          def method(self: T) -> T:
              pass

      expr = C().method()
      #└ TYPE C
      """)

    @Test
    @TestFor(issues = ["PY-24990"])
    fun `self annotated with type var on subclass instance`() = test("""
      from typing import TypeVar

      T = TypeVar('T')

      class C:
          def method(self: T) -> T:
              pass

      class D(C):
          pass

      expr = D().method()
      #└ TYPE D
      """)

    @Test
    @TestFor(issues = ["PY-24990"])
    fun `self annotated with type var receiver union type`() = test("""
      from typing import TypeVar

      T = TypeVar('T')

      class Base:
          def method(self: T) -> T:
              pass

      class A(Base):
          pass

      class B(Base): 
          pass

      expr = (A() or B()).method()
      #└ TYPE A | B
      """)

    @Test
    @TestFor(issues = ["PY-24990"])
    fun `self annotated instance method called on class object`() = test(TestOptions(assertRecursionPrevention = false), """
      from typing import TypeVar

      T = TypeVar('T')

      class C:
          def method(self: T) -> T:
              pass

      class D(C):
          pass

      expr = C.method(D())
      #│     ^^^^^^^^^^^^^ WARNING 'method' is not callable
      #└ TYPE Unknown
      """)

    @Test
    @TestFor(issues = ["PY-24990"])
    fun `self annotated in type comment on same class instance`() = test("""
      from typing import TypeVar

      T = TypeVar('T')

      class C:
          def method(self):
              # type: (T) -> T
              pass

      expr = C().method()
      #└ TYPE C
      """)

    @Test
    @TestFor(issues = ["PY-24990"])
    fun `self annotated in type comment on subclass instance`() = test("""
      from typing import TypeVar

      T = TypeVar('T')

      class C:
          def method(self):
              # type: (T) -> T
              pass

      class D(C):
          pass

      expr = D().method()
      #└ TYPE D
      """)

    @Test
    @TestFor(issues = ["PY-33663"])
    fun `annotated self return property`() = test("""
      from typing import TypeVar

      T = TypeVar("T")

      class A:
          @property
          def foo(self: T) -> T:
              pass

      expr = A().foo
      #└ TYPE A
      """)

    @Test
    fun `ancestor property returns self`() = test(
      """
      class Master:
          @property
          def me(self):
              return self
      class Child(Master):
          pass
      child = Child()
      expr = child.me
      #└ TYPE Child
      """,
    )

    @Test
    fun `self in docstring`() = test(
      """
      class C:
          def foo(self):
              '''
              :type self: int
              '''
              expr = self
      #       └ TYPE int
      """,
    )
  }

  @Nested
  inner class SelfTypeCheckerInspections {
    @Test
    fun `parameter Self`() = test("""
      from typing import Self, Callable

      class Shape:
          def difference(self, other: Self) -> float: ...

          def apply(self, f: Callable[[Self], None]) -> None: ...


      class Circle(Shape):
          pass


      def fCircle(c: Circle):
        pass


      def fShape(sh: Shape):
        pass


      sh = Shape()
      cir = Circle()

      sh.difference(cir)
      sh.difference(sh)
      cir.difference(cir)
      cir.difference(sh) # WARNING Expected type 'Circle' (matched generic type 'Self@Shape'), got 'Shape' instead

      cir.apply(fCircle)
      cir.apply(fShape) # WARNING Expected type '(Circle) -> None' (matched generic type '(Self@Shape) -> None'), got '(sh: Shape) -> None' instead
      sh.apply(fCircle)
      sh.apply(fShape)
      """)

    @Test
    @TestFor(issues = ["PY-53104"])
    fun `parameter type Self`() = test("""
      from typing import Self, Callable

      class MyClass:
          def foo(self, bar: Type[Self]) -> None: ...
      #                      ^^^^ ERROR Unresolved reference 'Type'


      class SubClass(MyClass):
          pass


      myClass = MyClass()
      subClass = MySubClass()
      #          ^^^^^^^^^^ ERROR Unresolved reference 'MySubClass'

      myClass.foo(myClass)
      myClass.foo(subClass)
      myClass.foo(MyClass)
      myClass.foo(SubClass)

      subClass.foo(myClass)
      subClass.foo(subClass)
      subClass.foo(MyClass)
      subClass.foo(SubClass)
      """)

    @Test
    @TestFor(issues = ["PY-53104"])
    fun `parameter type Self union`() = test("""
      from typing import Self, Callable

      class MyClass:
          def foo(self, bar: Self | None | int) -> None: ...


      class SubClass(MyClass):
          pass


      myClass = MyClass()
      subClass = SubClass()

      myClass.foo(myClass)
      myClass.foo(subClass)
      myClass.foo(42)
      myClass.foo(None)
      myClass.foo("") # WARNING Expected type 'MyClass | None | int' (matched generic type 'Self@MyClass | None | int'), got 'Literal[""]' instead

      subClass.foo(myClass) # WARNING Expected type 'SubClass | None | int' (matched generic type 'Self@MyClass | None | int'), got 'MyClass' instead
      subClass.foo(subClass)
      subClass.foo(42)
      subClass.foo(None)
      subClass.foo("") # WARNING Expected type 'SubClass | None | int' (matched generic type 'Self@MyClass | None | int'), got 'Literal[""]' instead
      """)

    @Test
    @TestFor(issues = ["PY-53104"])
    fun `parameter type Self return as parameter`() = test("""
      from typing import Self, Callable

      class MyClass:
          def foo(self, bar: Self) -> Self: ...


      class SubClass(MyClass):
          pass


      myClass = MyClass()
      subClass = SubClass()

      myClass.foo(myClass.foo(myClass))
      myClass.foo(subClass.foo(subClass))
      myClass.foo(myClass.foo(subClass))
      myClass.foo(subClass.foo(myClass)) # WARNING Expected type 'SubClass' (matched generic type 'Self@MyClass'), got 'MyClass' instead

      subClass.foo(myClass.foo(myClass)) # WARNING Expected type 'SubClass' (matched generic type 'Self@MyClass'), got 'MyClass' instead
      subClass.foo(subClass.foo(subClass))
      subClass.foo(myClass.foo(subClass)) # WARNING Expected type 'SubClass' (matched generic type 'Self@MyClass'), got 'MyClass' instead
      subClass.foo(subClass.foo(myClass)) # WARNING Expected type 'SubClass' (matched generic type 'Self@MyClass'), got 'MyClass' instead
      """)

    @Test
    @TestFor(issues = ["PY-79220"])
    fun `annotated self specific parameterization`() = test("""
      class A[T]:
          def foo(x: A[int]) -> None: ...

          @classmethod
          def bar(x: type[A[int]]) -> None: ...

      A[int]().foo()
      A[str]().foo() # WARNING Expected type 'A[int]', got 'A[str]' instead

      A[int].bar()
      A[int]().bar()
      A[str].bar() # WARNING Expected type 'type[A[int]]', got 'type[A[str]]' instead
      A[str]().bar() # WARNING Expected type 'type[A[int]]', got 'type[A[str]]' instead
      """)

    @Test
    @TestFor(issues = ["PY-79220"])
    fun `annotated self against union receiver`() = test("""
      class A:
          def foo(self: A): ...

      class B:
          def foo(self: B): ...

      class C[T]:
          def foo(self: C[int]): ...

      def f(x: A | B, y: A | B | C[str]):
          x.foo()
          y.foo() # TODO: Expected warning: 'C[str]' not assignable to 'C[int]'
      """)

    @Test
    @TestFor(issues = ["PY-79220"])
    fun `unannotated self in metaclass`() = test("""
      class Meta(type):
          def foo(cls): ...

      class Class(metaclass=Meta): ...

      Class.foo()
      Meta("T", (), {}).foo()
      """)

    @Test
    @TestFor(issues = ["PY-56785"])
    fun `Self no inspection on return Self method`() = test("""
      from typing import Self


      class Builder:
          def foo(self) -> Self:
              result = self.bar()
              return result

          def bar(self) -> Self:
              pass
      """)

    @Test
    @TestFor(issues = ["PY-56785"])
    fun `Self classmethod return cls no highlighting`() = test("""
      from typing import Self

      class Shape:

          def __init__(self, scale: float):
              self.scale = None

          @classmethod
          def from_config(cls, config: dict[str, float]) -> Self:
              return cls(config["scale"])
      """)

    @Test
    fun `Self vs specific class in return`() = test("""
      from typing import Self
      class Shape:
         def method2(self) -> Self:
             # This should result in a type error.
             return Shape()  # E
      #             ^^^^^^^ WARNING Expected type 'Self@Shape', got 'Shape' instead

         def method3(self) -> Self:
             return self # OK
      """)

    @Test
    @TestFor(issues = ["PY-76860"])
    fun `Self vs specific class in target expr`() = test("""
      from typing import Self
      class Shape:
         def method2(self):
             my_instance: Self = Shape() # E
      #                          ^^^^^^^ WARNING Expected type 'Self@Shape', got 'Shape' instead
             my_instance: Self = self # OK
      """)

    @Test
    @TestFor(issues = ["PY-76860"])
    fun `Self vs specific super class in ancestor`() = test("""
      from typing import Self, override
      class Shape:
         def method2(self) -> Self:
             return self

      class Circle(Shape):
          @override
          def method2(self) -> Self:
              return Shape()
      #              ^^^^^^^ WARNING Expected type 'Self@Circle', got 'Shape' instead
      """)

    @Test
    @TestFor(issues = ["PY-76860"])
    fun `specific class instead of Self in call expr`() = test("""
      from typing import Self
      class Shape:
          def method2(self):
              self.method3(Shape()) # E
      #                    ^^^^^^^ WARNING Expected type 'Self@Shape', got 'Shape' instead
              self.method3(self) # OK
              self.method4([Shape()]) # E
      #                    ^^^^^^^^^ WARNING Expected type 'list[Self@Shape]', got 'list[Shape]' instead
              self.method4([self])  # OK
              ...

          def method3(self, x: Self): ...
          def method4(self, x: list[Self]): ...
      """)

    @Test
    @TestFor(issues = ["PY-76886"])
    fun `Self in class methods`() = test("""
      from typing import Self
      class Shape:
         @classmethod
         def method1(cls) -> Self:
             return cls() # OK
         @classmethod
         def method2(cls) -> Self:
             return cls # E
      #             ^^^ WARNING Expected type 'Self@Shape', got 'type[Self@Shape]' instead
         @classmethod
         def method3(cls) -> type[Self]:
             return cls() # E
      #             ^^^^^ WARNING Expected type 'type[Self@Shape]', got 'Self@Shape' instead
         @classmethod
         def method4(cls) -> type[Self]:
             return cls # OK
      """)

    @Test
    fun `Self vs dunder class`() = test("""
      from typing import Self
      class ConcreteComparable:
          def clone(self) -> Self:
              return self.__class__() # OK
          def clone_cls(self) -> type[Self]:
              return self.__class__ # OK
      """)

    @Test
    fun `Self in unions`() = test("""
      from typing import Self
      class MyClass:
          def foo(self):
              y1: Self | None = self
              y2: Self | None = None
              y3: Self | int = self
              y4: Self | int = 3
              y5: Self | int | list[Self] = [self]
              y6: Self | int | list[Self] = [3] # E
      #                                     ^^^ WARNING Expected type 'Self@MyClass | int | list[Self@MyClass]', got 'list[Literal[3]]' instead
              y7: Self | int | list[Self] = "str" # E
      #                                     ^^^^^ WARNING Expected type 'Self@MyClass | int | list[Self@MyClass]', got 'Literal["str"]' instead
      """)

    @Test
    fun `Self assigned to other type good`() = test("""
      from typing import Self

      class Base: ...

      class Shape(Base):
          def good_meth(self):
              #y1: Self = self
              #y2: Base = self # OK
              #y3: object = self
              #y5: Shape = self
              y6: Self | None = self

          @classmethod
          def good_cls(cls):
              y1: type[Self] = cls
              y2: type[Shape] = cls
              y3: type[Base] = cls
              y4: type[object] = cls
              y5: Self = cls()
              y6: Base = cls()

      class Circle(Shape): ...
      """)

    @Test
    fun `Self assigned to other type bad`() = test("""
      from typing import Self

      class Base: ...

      class Shape(Base):

          def bad_meth(self):
              y1: int = self
      #                 ^^^^ WARNING Expected type 'int', got 'Self@Shape' instead
              y2: type[Shape] = self
      #                         ^^^^ WARNING Expected type 'type[Shape]', got 'Self@Shape' instead
              y21: Shape = self
              y22: Base = self
              y3: type[Circle] = self
      #                          ^^^^ WARNING Expected type 'type[Circle]', got 'Self@Shape' instead
              y4: type[Self] = self
      #                        ^^^^ WARNING Expected type 'type[Self@Shape]', got 'Self@Shape' instead
              y5: Circle = self
      #                    ^^^^ WARNING Expected type 'Circle', got 'Self@Shape' instead

          @classmethod
          def bad_cls(cls):
              y1: int = cls
      #                 ^^^ WARNING Expected type 'int', got 'type[Self@Shape]' instead
              y2: Shape = cls
      #                   ^^^ WARNING Expected type 'Shape', got 'type[Self@Shape]' instead
              y21: type[Shape] = cls
              y22: type[Base] = cls
              y3: Base = cls
      #                  ^^^ WARNING Expected type 'Base', got 'type[Self@Shape]' instead
              y4: Circle = cls
      #                    ^^^ WARNING Expected type 'Circle', got 'type[Self@Shape]' instead
              y5: Self = cls
      #                  ^^^ WARNING Expected type 'Self@Shape', got 'type[Self@Shape]' instead
              y6: Circle = cls()
      #                    ^^^^^ WARNING Expected type 'Circle', got 'Self@Shape' instead

      class Circle(Shape): ...
      """)

    @Test
    @TestFor(issues = ["PY-85974"])
    fun `Self attribute assignment`() = test("""
      from typing import Self

      class Node:
          next: Self | None

      c: Node
      c.next = Node()
      """)
  }

  @Nested
  inner class ModuleAndImportAttributeTypes {
    @Test
    @TestFor(issues = ["PY-86315"])
    fun `import alias type is module`() = test(
      """
      import imported as expr
      #                  └ TYPE imported
      """,
      "imported.py" to """
        if _:
            x: int
        else:
            x: str
        """,
    )

    @Test
    @TestFor(issues = ["PY-86315"])
    fun `imported attribute type from version-guarded module`() = test(
      """
      from imported import x as expr
      #                         └ TYPE str | int
      """,
      "imported.py" to """
        if _:
            x: int
        else:
            x: str
        """,
    )
  }

  @Nested
  inner class PyAnyMigrationMirrors {
    @Test
    @Disabled("PyAnyType")
    fun `classmethod returning Self (py-any)`() = test(
      TestOptions(enablePyAnyType = true),
      """
      from typing import Self


      class Shape:
          @classmethod
          def from_config(cls, config: dict[str, float]) -> Self:
              return cls(config["scale"])


      class Circle(Shape):
          pass


      expr = Circle.from_config({})
      #└ TYPE Circle
      """,
    )

    @Test
    @Disabled("PyAnyType")
    fun `LiteralString join on plain str receiver (py-any)`() = test(
      TestOptions(enablePyAnyType = true),
      """
      from typing_extensions import LiteralString
      x: str
      xs: list[LiteralString]
      expr = x.join(xs)
      #└ TYPE str
      """,
    )

    @Test
    @Disabled("PyAnyType")
    fun `ClassVar type resolved from annotation (py-any)`() = test(
      TestOptions(enablePyAnyType = true),
      """
      from typing import ClassVar
      class A:
          x: ClassVar[int] = 1
      expr = A.x
      #└ TYPE int
      """,
    )
  }
}
