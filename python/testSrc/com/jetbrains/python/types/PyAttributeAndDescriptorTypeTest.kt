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
 * Type and type-checker tests for properties, descriptors and attributes:
 * `property` type inference, descriptor `__get__`/`__set__` (including generic descriptors and
 * access via instance/class), instance & class attribute type inference, attribute annotations,
 * `ClassVar`, `Final` type inference, `__slots__` typing, lazy/conditional attribute init,
 * attribute reassignment, and `Self`-returning properties/methods.
 */
@Subsystems.Typing
@Components.TypeInference
@Layers.Functional
class PyAttributeAndDescriptorTypeTest : PyCodeInsightTestCase() {

  @Nested
  inner class PropertyTypeInference {
    @Test
    fun `property attribute accessed on class`() = test("""
      class C:
          x = property(lambda self: 'foo', None, None)
      expr = C.x
      # └ TYPE property
      """.trimIndent())

    @Test
    fun `property attribute accessed on instance`() = test("""
      class C:
          x = property(lambda self: 'foo', None, None)
      c = C()
      expr = c.x
      #└ TYPE Literal["foo"]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-9605"])
    fun `property returns callable`() = test("""
      class C(object):
          @property
          def foo(self):
              return lambda: 0

      c = C()
      expr = c.foo
      # └ TYPE () -> Literal[0]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-76219"])
    fun `property type accessed via bounded type parameter`() = test("""
      class K:
          _text: str
          @property
          def text(self) -> str:
              return self._text

      def bar[T:K](k : T):
          expr = k.text
      #   └ TYPE str
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-13159"])
    fun `abstractproperty result type`() = test("""
      import abc
      class D(abc.ABC):
          @abc.abstractproperty
          def foo(self):
              return 'foo'
      def f(d: D):
        expr = d.foo
      # └ TYPE Literal["foo"]
      """.trimIndent())

    @Test
    fun `abstractproperty result type imported with from`() = test("""
      from abc import abstractproperty, ABC
      class D(ABC):
          @abstractproperty
          def foo(self):
              return 'foo'
      def f(d: D):
        expr = d.foo
      # └ TYPE Literal["foo"]
      """.trimIndent())

    @Test
    fun `property returning callable is called`() = test("""
      from typing import Iterator, Callable
      class Foo:
          def iterate(self) -> Iterator[int]:
              pass
          @property
          def foo(self) -> Callable[[], Iterator[int]]:
              return self.iterate
      expr = Foo().foo()
      #└ TYPE Iterator[int]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-43122"])
    fun `attribute initialized from property of parameter`() = test("""
      class A:
          def __init__(self) -> None:
              pass

          @property
          def a_property(self) -> str:
              return 'foo'


      class B:
          def __init__(self, a: A) -> None:
              self.b_attr = a.a_property


      a = A()
      b = B(a)
      expr = b.b_attr
      #└ TYPE str
      """.trimIndent())

    @Test
    fun `attribute initialized from property of imported class`() = test("""
      from mod import A, B

      a = A()
      b = B(a)
      expr = b.b_attr
      #└ TYPE str
      """.trimIndent(),
      "mod.py" to """
        class A:
            def __init__(self) -> None:
                pass

            @property
            def a_property(self) -> str:
                return 'foo'


        class B:
            def __init__(self, a: A) -> None:
                self.b_attr = a.a_property
        """.trimIndent())

    @Test
    fun `imported property whose result is unknown`() = test("""
      from temporary import get_class
      class Example:
          def __init__(self):
              expr = self.ins_class
      #       └ TYPE Type mismatch for code analysis context ('Unknown') and user initiated context ('type[str]')
          @property
          def ins_class(self):
              return get_class()
      """.trimIndent(),
      "temporary.py" to "def get_class():\n    return str",
    )

    @Test
    @TestFor(issues = ["PY-6426"])
    fun `property created from a function reference is fine`() = test("""
      class Foo:
          def _get_serial_number(self) -> str:
              return "42"

          serial_number = property(_get_serial_number)
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-88967"])
    fun `metaclass property takes precedence over class property on class`() = test("""
      class Meta(type):
          @property
          def prop(cls) -> str: ...

      class C(metaclass=Meta):
          @property
          def prop(self) -> int: ...

      expr0 = Meta.prop
      #└ TYPE property

      expr = C.prop
      # └ TYPE str

      expr2 = C().prop
      #└ TYPE int
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-88967"])
    fun `metaclass property takes precedence over class attribute on class`() = test("""
      class Meta(type):
          @property
          def prop(cls) -> str: ...

      class C(metaclass=Meta):
          prop: int = 0

      expr = C.prop
      # └ TYPE str
      
      expr1 = C().prop
      # └ TYPE int
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-88967"])
    fun `metaclass method does not shadow class attribute on class`() = test("""
      class Meta(type):
          def member(cls) -> str: ...

      class C(metaclass=Meta):
          member: int = 0

      expr = C.member
      # └ TYPE int
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-88967"])
    fun `metaclass-only property accessed on class invokes the getter`() = test("""
      class MyMeta(type):
          @property
          def attr(cls) -> int:
              return 1

      class A(metaclass=MyMeta): ...

      expr = A.attr
      # └ TYPE int
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-88967"])
    fun `class-only property accessed on class returns the descriptor`() = test("""
      class MyMeta(type): ...

      class A(metaclass=MyMeta):
          @property
          def attr(self) -> int:
              return 2

      expr = A.attr
      # └ TYPE property
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-88967"])
    fun `class-only property accessed on instance invokes the getter`() = test("""
      class MyMeta(type): ...

      class A(metaclass=MyMeta):
          @property
          def attr(self) -> int:
              return 2

      expr = A().attr
      # └ TYPE int
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-88967"])
    fun `metaclass property is ignored when accessing attr on instance`() = test("""
      class MyMeta(type):
          @property
          def attr(cls) -> int:
              return 3

      class A(metaclass=MyMeta):
          @property
          def attr(self) -> str:
              return "3"

      expr2 = A().attr
      #└ TYPE str
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-88967"])
    fun `non-descriptor metaclass attribute does not shadow class property`() = test("""
      class MyMeta(type):
          attr = "4"

      class A(metaclass=MyMeta):
          @property
          def attr(self) -> int:
              return 4

      expr = A.attr
      # └ TYPE property
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-6803"])
    fun `property and factory function`() = test("""
      class C(object):
          @property
          def foo(self):
              return 'bar'

      def f():
          return C()

      def test():
          f().foo + 1
      #             └ WARNING No overload of '__add__' matches the arguments. Argument types: (Literal[1]). Expected one of: (value: LiteralString), (value: str)
      """.trimIndent())
  }

  @Nested
  inner class AttributeTypeInference {
    @Test
    @TestFor(issues = ["PY-7040"])
    fun `instance attribute shadows class attribute of different type`() = test("""
      class C(object):
          foo = 'str1'

          def __init__(self):
              self.foo = 3
              expr = self.foo
      #       └ TYPE Literal[3]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-6584"])
    fun `class attribute type from class docstring via class`() = test("""
      class C(object):
          '''
          :type foo: int
          '''
          foo = None

      expr = C.foo
      #└ TYPE int
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-6584"])
    fun `class attribute type from class docstring via instance`() = test("""
      class C(object):
          '''
          :type foo: int
          '''
          foo = None

      expr = C().foo
      #└ TYPE int
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-6584"])
    fun `instance attribute type from class docstring`() = test("""
      class C(object):
          '''
          :type foo: int
          '''
          def __init__(self, bar):
              self.foo = bar

      def f(x):
          expr = C(x).foo
      #   └ TYPE int
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-8953"])
    fun `self type from method docstring`() = test("""
      class C(object):
          def foo(self):
              '''
              :type self: int
              '''
              expr = self
      #       └ TYPE int
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-28052"])
    fun `class attribute annotated as Any`() = test("""
      from typing import Any


      class MyClass:
          arbitrary: Any = 42


      expr = MyClass().arbitrary
      #└ TYPE Any
      """.trimIndent())

    @Test
    fun `class attribute annotation with explicit Any survives reassignment`() = test("""
      from typing import Any

      class C:
          attr: Any = None

          def m(self, x):
              self.attr = x
              expr = self.attr
      #       └ TYPE Any
      """.trimIndent())

    @Test
    fun `class attribute annotated ahead of time in another file`() = test("""
      from other import C

      expr = C().attr
      # └ TYPE int
      """.trimIndent(),
      "other.py" to """
        class C:
            attr: int
            attr, _ = None, None
        """.trimIndent())

    @Test
    fun `instance attribute annotation accessed on instance`() = test("""
      class C:
          attr: int

      expr = C().attr
      #└ TYPE int
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-24729"])
    fun `annotated instance attribute reference outside class`() = test("""
      class C:
          attr: int

          def __init__(self):
              self.attr = 'foo' # WARNING Expected type 'int', got 'Literal["foo"]' instead

      expr = C().attr
      #└ TYPE int
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-24729"])
    fun `annotated instance attribute reference inside class`() = test("""
      class C:
          attr: int

          def __init__(self):
              self.attr = 'foo' # WARNING Expected type 'int', got 'Literal["foo"]' instead

          def m(self):
              expr = self.attr
      #       └ TYPE int
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-24729"])
    fun `annotated instance attribute in other file`() = test("""
      from other import C

      expr = C().attr
      #└ TYPE int
      """.trimIndent(),
      "other.py" to """
        class C:
            attr: int

            def __init__(self):
                self.attr = 'foo'
        """.trimIndent())

    @Test
    @TestFor(issues = ["PY-79480"])
    fun `inherited attribute with type annotation in parent constructor`() = test("""
      import typing

      class FakeBase:
          def __init__(self):
              self._some_var: typing.Optional[str] = ""

      class Fake(FakeBase):
          def __init__(self):
              super().__init__()
              self._some_var = None

          def some_method(self):
              expr = self._some_var
      #       └ TYPE str | None
      """.trimIndent())

    @Test
    fun `inherited attribute with type annotation in parent`() = test("""
      import typing

      class FakeBase:
          _some_var: typing.Optional[str]

      class Fake(FakeBase):
          def __init__(self):
              super().__init__()
              self._some_var = None

          def some_method(self):
              expr = self._some_var
      #       └ TYPE str | None
      """.trimIndent())

    @Test
    fun `inherited attribute with type annotation in child`() = test("""
      import typing

      class FakeBase:
          def __init__(self):
              self._some_var = 1

      class Fake(FakeBase):
          def __init__(self):
              super().__init__()
              self._some_var: typing.Optional[str] = None

          def some_method(self):
              expr = self._some_var
      #       └ TYPE str | None
      """.trimIndent())

    @Test
    fun `explicit None attribute annotation`() = test("""
      class A:
          x: None

      def f(a: A):
          expr = a.x
      #   └ TYPE None
      """.trimIndent())

    @Test
    fun `qualified attribute type not confused with same name parameter`() = test("""
      class Beta:
          x: int

          def doit(self, x: str):
              expr = self.x and x
      #       └ TYPE int | str
      """.trimIndent())

    @Test
    fun `builtin generic alias in stubbed class attribute annotation does not resolve to inherited method`() = test("""
      from sample import A

      a = A()
      expr = a.b
      #└ TYPE dict[int, str]
      """.trimIndent(),
      "sample.py" to """
        class Base:
            def dict(self):
                return None

        class A(Base):
            b: dict[int, str] = {}
        """.trimIndent())

    @Test
    fun `missing attribute in intersection member`() = test("""
      class A:
          ...

      class B:
          attr: int = 1

      def f(p: B):
          if isinstance(p, A):
              expr = p.attr
      #       └ TYPE int
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-7340"])
    fun `field with none in stub`() = test(
      """
      from m1 import C

      def f(x):
          '''
          :type x: int
          '''
          pass

      def test():
          f(C.foo)
      """.trimIndent(),
      "m1.py" to """
      class C(object):
          foo = None
      """.trimIndent(),
    )

    @Test
    @TestFor(issues = ["PY-14222"])
    fun `recursive dict attribute`() = test("""
      class C:
          def f(self, x):
              self.foo = x
              self.foo = {'foo': self.foo}
              return self.foo['foo'] + 10
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-26354"])
    @TestCaseOptions(additionalSdkRoots = [SdkRoot("packages", OrderRootTypeEnum.CLASSES)], assertRecursionPrevention = false)
    fun `initializing attrs`() = test("""
      import attr
      import typing

      @attr.s
      class Weak1:
          x = attr.ib()
          y = attr.ib(default=0)
          z = attr.ib(default=attr.Factory(list))

      Weak1(1, "str", 2)
      #        │      └ WARNING Expected type 'list', got 'Literal[2]' instead
      #        ^^^^^ WARNING Expected type 'int', got 'Literal["str"]' instead


      @attr.s
      class Weak2:
          x = attr.ib()

          @x.default
          def __init_x__(self):
              return 1

      Weak2("str")


      @attr.s
      class Strong:
          x = attr.ib(type=int)
          y = attr.ib(default=0, type=int)
          z = attr.ib(default=attr.Factory(list), type=typing.List[int])

      Strong(1, "str", ["str"])
      #         │      ^^^^^^^ WARNING Expected type 'list[int]', got 'list[Literal["str"]]' instead
      #         ^^^^^ WARNING Expected type 'int', got 'Literal["str"]' instead
      """.trimIndent())

  }

  @Nested
  inner class DunderGetattr {
    @Test
    fun `dunder getattr return type`() = test("""
      class MyClass:
          def __getattr__(self, item) -> 'MyClass':
              pass

      expr = MyClass().attr
      #└ TYPE MyClass
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-90894"])
    fun `dunder getattr generic return type`() = test("""
      class Box[T]:
          def __getattr__(self, item) -> T:
              raise NotImplementedError

      def foo(box: Box[int]):
          expr = box.whatever
      #   └ TYPE int
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-90894"])
    fun `dunder getattr overloaded by literal name`() = test("""
      from typing import Literal, overload

      class C:
          @overload
          def __getattr__(self, item: Literal["foo"]) -> int: ...
          @overload
          def __getattr__(self, item: Literal["bar"]) -> str: ...
          def __getattr__(self, item):
              raise NotImplementedError

      def f(c: C):
          foo = c.foo
      #   └ TYPE int
          bar = c.bar
      #   └ TYPE str
          baz = c.baz
      #   └ TYPE Unknown
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-85595"])
    fun `dunder getattr not called for explicit Any annotation`() = test("""
      from typing import Any

      class MyClass:
          def __init__(self):
              self.attr: Any = 42

          def __getattr__(self, item) -> 'MyClass':
              pass

      def foo(obj: MyClass):
          expr = obj.attr
      #   └ TYPE Any
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-28017"])
    fun `module with get attr`() = test("""
      import mymod

      def foo(mod):
          return mod.myfunc

      print(foo(mymod)())
      """.trimIndent(),
      "mymod.py" to """
      def myhiddenfunc():
          return "ok"

      def __getattr__(name):
          if name == "myfunc":
              return myhiddenfunc
          raise AttributeError
      """.trimIndent())
  }

  @Nested
  inner class ClassVarTyping {
    @Test
    fun `ClassVar type resolved from annotation`() = test("""
      from typing import ClassVar
      class A:
          x: ClassVar[int] = 1
      expr = A.x
      #└ TYPE int
      """.trimIndent())

    @Test
    fun `ClassVar type resolved from type comment`() = test("""
      from typing import ClassVar
      class A:
          x = 1  # type: ClassVar[int]
      expr = A.x
      #└ TYPE int
      """.trimIndent())
  }

  @Nested
  inner class FinalTypeInference {
    @Test
    fun `Final with explicit type and value`() = test("""
      from typing_extensions import Final
      expr: Final[int] = undefined # ERROR Unresolved reference 'undefined'
      #└ TYPE int
      """.trimIndent())

    @Test
    fun `Final without type infers literal of value`() = test("""
      from typing_extensions import Final
      expr: Final = 5
      #└ TYPE Literal[5]
      """.trimIndent())

    @Test
    fun `Final with explicit type only`() = test("""
      from typing_extensions import Final
      expr: Final[int] # WARNING 'Final' name should be initialized with a value
      #└ TYPE int
      """.trimIndent())

    @Test
    fun `Final without type infers list type from value`() = test("""
      from typing_extensions import Final
      expr: Final = [1, 2]
      #└ TYPE list[int]
      """.trimIndent())

    @Test
    fun `Final with explicit type in type comment`() = test("""
      from typing_extensions import Final
      expr = undefined  # type: Final[int]
      #│     ^^^^^^^^^ ERROR Unresolved reference 'undefined'
      #└ TYPE int
      """.trimIndent())

    @Test
    fun `Final without type in type comment infers literal`() = test("""
      from typing_extensions import Final
      expr = 5  # type: Final
      #└ TYPE Literal[5]
      """.trimIndent())
  }

  @Nested
  inner class DunderSlotsTyping {
    @Test
    @TestFor(issues = ["PY-83206"])
    fun `empty slots are not disjoint`() = test("""
      class A:
          __slots__ = []

      class B:
          __slots__ = []

      def foo(x: A) -> None:
          if isinstance(x, B):
              expr = x
      #       └ TYPE A & B
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-22222", "PY-29233"])
    fun `pass class with dunder slots to method that uses slotted attribute`() = test("""
      class A(object):
          __slots__ = 'x', 'y'


      class B(object):
          __slots__ = ['x']

      class C(B):
          pass

      class D(object):
          pass

      class E(D):
          __slots__ = ['x']


      class F:
          __slots__ = ['x']


      def copy_values(a):
          print(a.x)

      copy_values(A())
      copy_values(C())
      copy_values(E())
      copy_values(D())
      #           ^^^ WARNING Type 'D' doesn't have expected attribute 'x'
      """.trimIndent())
  }

  @Nested
  inner class LazyAndConditionalAttributeInit {
    @Test
    fun `lazy attribute initialization`() = test("""
      class C:
          def __init__(self):
              self.attr = None

          def m(self):
              if self.attr is None:
                  self.attr = 42
              expr = self.attr
      #       └ TYPE UnsafeUnion[None, Unknown] | Literal[42]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-21175"])
    fun `assignment to attribute of call result with name of local variable`() = test("""
      def f(g):
          x = 42
          if True:
              g().x = 'foo'
          expr = x
      #   └ TYPE Literal[42]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-40882"])
    fun `type hinted instance attribute without assignment is resolved`() = test("""
      class A:
          def __init__(self, a: int):
              self.a: int | None
              if bool():
                  self.a = a
              else:
                  self.a = None


      class B:
          def __init__(self):
              self.b: int

          def get_b(self) -> int:
              return self.b


      print(A(1).a)

      expr = B().b
      # └ TYPE int
      """.trimIndent())
  }

  @Nested
  inner class SelfReturningPropertiesAndMethods {
    @Test
    fun `property returning self resolves to subclass`() = test("""
      class Master(object):
          @property
          def me(self):
              return self
      class Child(Master):
          pass
      child = Child()
      expr = child.me
      # └ TYPE Child
      """.trimIndent())

    @Test
    fun `annotated self return property`() = test("""
      from typing import TypeVar

      T = TypeVar("T")

      class A:
          @property
          def foo(self: T) -> T:
              pass

      expr = A().foo
      #└ TYPE A
      """.trimIndent())

    @Test
    fun `method returning self in generator`() = test("""
      class A:
          def foo(self):
              yield self
              return self
      class B(A):
          pass
      expr = B().foo()
      #└ TYPE Generator[B, Unknown, B]
      """.trimIndent())

    @Test
    fun `method returning self in union`() = test("""
      class A:
          def foo(self, x):
              if x:
                  return self
              else:
                  return 1
      class B(A):
          pass
      expr = B().foo(abc) # ERROR Unresolved reference 'abc'
      #└ TYPE B | Literal[1]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-27143"])
    fun `classmethod returning instance via cls called on class`() = test("""
      class Base:
          @classmethod
          def instance(cls):
              return cls()
      class Derived(Base):
          pass
      expr = Derived.instance()
      #└ TYPE Derived
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-27143"])
    fun `classmethod returning instance via cls called on instance`() = test("""
      class Base:
          @classmethod
          def instance(cls):
              return cls()
      class Derived(Base):
          pass
      expr = Derived().instance()
      #└ TYPE Derived
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-27143"])
    fun `method returning self called unbound`() = test("""
      class Base:
          def instance(self):
              return self
      class Derived(Base):
          pass
      expr = Derived.instance(Derived())
      #└ TYPE Derived
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-27143"])
    fun `method returning self called on instance`() = test("""
      class Base:
          def instance(self):
              return self
      class Derived(Base):
          pass
      expr = Derived().instance()
      #└ TYPE Derived
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-30861"])
    fun `specified return type is not replaced with self`() = test("""
      from collections import defaultdict
      data = defaultdict(dict)
      expr = data['name']
      #└ TYPE dict
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-53104"])
    fun `method returning typing Self resolves to subclass`() = test("""
      from typing import Self

      class A:
          def foo(self) -> Self:
              ...
      class B(A):
          pass
      expr = B().foo()
      #└ TYPE B
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-53104"])
    fun `method returning list of typing Self`() = test("""
      from typing import Self

      class A:
          def foo(self) -> list[Self]:
              ...
      class B(A):
          pass
      expr = B().foo()
      #└ TYPE list[B]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-53104"])
    fun `classmethod returning typing Self`() = test("""
      from typing import Self


      class Shape:
          @classmethod
          def from_config(cls, config: dict[str, float]) -> Self:
              return cls(config["scale"]) # WARNING Unexpected argument


      class Circle(Shape):
          pass


      expr = Circle.from_config({})
      #└ TYPE Circle
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-53104"])
    fun `classmethod returning typing Self in nested class`() = test("""
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
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-53104"])
    fun `method returning typing Self defined in imported file`() = test("""
      from other import Clazz
      clz = Clazz()
      expr = clz.foo()
      #└ TYPE Clazz
      """.trimIndent(),
      "other.py" to """
        from typing import Self

        class Clazz:
            def foo(self) -> Self:
                return self
        """.trimIndent())

    @Test
    @TestFor(issues = ["PY-53104"])
    fun `method returning typing Self on receiver of union type`() = test("""
      from typing import Self


      class C:
          def method(self) -> Self:
              return self


      if bool():
          x = 42
      else:
          x = C()

      expr = x.method()
      #│       ^^^^^^ WEAK-WARNING Member 'Literal[42]' of 'Literal[42] | C' does not have attribute 'method'
      #└ TYPE C | Unknown
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-80622"])
    fun `augmented assignment with iadd returning typing Self`() = test("""
      from typing import Self

      class MutableContainer:
          def __iadd__(self, other: int) -> Self:
              return self

      m = MutableContainer()
      m += 1
      expr = m
      #└ TYPE MutableContainer
      """.trimIndent())
  }

  @Nested
  inner class SelfClsTypeVarAnnotations {
    @Test
    @TestFor(issues = ["PY-24990"])
    fun `self annotation same class instance`() = test("""
      from typing import TypeVar

      T = TypeVar('T')

      class C:
          def method(self: T) -> T:
              pass

      expr = C().method()
      #└ TYPE C
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-24990"])
    fun `self annotation subclass instance`() = test("""
      from typing import TypeVar

      T = TypeVar('T')

      class C:
          def method(self: T) -> T:
              pass

      class D(C):
          pass

      expr = D().method()
      #└ TYPE D
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-24990"])
    fun `cls annotation same class instance`() = test("""
      from typing import TypeVar, Type

      T = TypeVar('T')

      class C:
          @classmethod
          def factory(cls: Type[T]) -> T:
              pass

      expr = C.factory()
      #└ TYPE C
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-24990"])
    fun `cls annotation subclass instance`() = test("""
      from typing import TypeVar, Type

      T = TypeVar('T')

      class C:
          @classmethod
          def factory(cls: Type[T]) -> T:
              pass

      class D(C):
          pass

      expr = D.factory()
      #└ TYPE D
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-24990"])
    fun `cls annotation classmethod called on instance`() = test("""
      from typing import TypeVar, Type

      T = TypeVar('T')

      class C:
          @classmethod
          def factory(cls: Type[T]) -> T:
              pass

      class D(C):
          pass

      expr = D().factory()
      #└ TYPE D
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-24990"])
    fun `self annotation receiver union type`() = test("""
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
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-24990"])
    fun `cls annotation classmethod called on mixed instance and class object`() = test("""
      from typing import TypeVar, Type

      T = TypeVar('T')

      class Base:
          @classmethod
          def factory(cls: Type[T]) -> T:
              pass

      class A(Base):
          pass

      expr = (A or A()).factory()
      #└ TYPE A
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-24990"])
    fun `self annotation instance method called on class object`() = test("""
      from typing import TypeVar

      T = TypeVar('T')

      class C:
          def method(self: T) -> T:
              pass

      class D(C):
          pass

      expr = C.method(D())
      #└ TYPE D
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-24990"])
    fun `self annotation in type comment same class instance`() = test("""
      from typing import TypeVar

      T = TypeVar('T')

      class C:
          def method(self):
              # type: (T) -> T
              pass

      expr = C().method()
      #└ TYPE C
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-24990"])
    fun `self annotation in type comment subclass instance`() = test("""
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
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-24990"])
    fun `cls annotation in type comment same class instance`() = test("""
      from typing import TypeVar, Type

      T = TypeVar('T')

      class C:
          @classmethod
          def factory(cls):
              # type: (Type[T]) -> T
              pass

      expr = C.factory()
      #└ TYPE C
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-24990"])
    fun `cls annotation in type comment subclass instance`() = test("""
      from typing import TypeVar, Type

      T = TypeVar('T')

      class C:
          @classmethod
          def factory(cls):
              # type: (Type[T]) -> T
              pass

      class D(C):
          pass

      expr = D.factory()
      #└ TYPE D
      """.trimIndent())
  }

  @Nested
  inner class GenericDescriptors {
    @Test
    @TestFor(issues = ["PY-26184"])
    fun `generic type from descriptor`() = test("""
      import typing

      class MyDescriptor[T]:
          def __init__(self, requested_type: typing.Type[T]):
              self.requested_type = requested_type
          def __get__(self, instance: typing.Any, owner: typing.Any) -> T:
              raise Exception("Not implemented")

      class Test:
          member = MyDescriptor(list)
          def foo(self):
              test = self.member
              expr = test
      #       └ TYPE list
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-63737"])
    fun `generic descriptor with own type parameter in get binds the return type variable on instance access`() = test("""
      from typing import Callable, TypeVar, Generic
      T = TypeVar("T")
      T_co = TypeVar("T_co", covariant=True)
      class CachedSlotProperty(Generic[T, T_co]):
          def __init__(self, f: Callable[[T], T_co]) -> None:
              self.f = f
          def __get__(self, instance: T, owner: type[T]) -> T_co:
              return self.f(instance) + 1
      class Foo:
          @CachedSlotProperty
          def bar(self) -> int:
              return 42
      expr = Foo().bar
      #└ TYPE int
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-63737"])
    fun `instance access passes the owner class so a get typed with type T does not drop other bindings`() = test("""
      from typing import Callable
      class CachedSlotProperty[T, V]:
          def __init__(self, f: Callable[[T], V]) -> None: ...
          def __get__(self, instance: T, owner: type[T]) -> V: ...
      class Foo:
          bar: CachedSlotProperty[Foo, int]
      expr = Foo().bar
      #└ TYPE int
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-63737"])
    fun `generic descriptor subclass used as decorator accessed on instance`() = test("""
      from typing import Any, Callable, Generic, TypeVar, Union, overload

      _T = TypeVar("_T")

      class base(Generic[_T]):
          def __init__(self, fget: Callable[..., _T]): ...
          @overload
          def __get__(self, obj: None, cls: Any) -> "base[_T]": ...
          @overload
          def __get__(self, obj: object, cls: Any) -> _T: ...
          def __get__(self, obj: Any, cls: Any) -> Union["base[_T]", _T]: ...

      class memo(base[_T]):
          pass

      class C:
          @memo
          def x(self) -> int: ...

      c = C()
      expr = c.x
      #└ TYPE int
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-63737"])
    fun `generic descriptor TYPE_CHECKING alias used as decorator accessed on instance`() = test("""
      from typing import Any, Callable, Generic, TypeVar, Union, overload, TYPE_CHECKING

      _T = TypeVar("_T")

      class generic_fn_descriptor(Generic[_T]):
          def __init__(self, fget: Callable[..., _T]): ...
          @overload
          def __get__(self, obj: None, cls: Any) -> "generic_fn_descriptor[_T]": ...
          @overload
          def __get__(self, obj: object, cls: Any) -> _T: ...
          def __get__(self, obj: Any, cls: Any) -> Union["generic_fn_descriptor[_T]", _T]: ...

      if TYPE_CHECKING:
          memoized_property = generic_fn_descriptor
      else:
          memoized_property = generic_fn_descriptor

      class C:
          @memoized_property
          def x(self) -> int: ...

      c = C()
      expr = c.x
      #└ TYPE int
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-63737"])
    fun `generic descriptor subclass used as decorator accessed on class`() = test("""
      from typing import Any, Callable, Generic, TypeVar, Union, overload

      _T = TypeVar("_T")

      class base(Generic[_T]):
          def __init__(self, fget: Callable[..., _T]): ...
          @overload
          def __get__(self, obj: None, cls: Any) -> "base[_T]": ...
          @overload
          def __get__(self, obj: object, cls: Any) -> _T: ...
          def __get__(self, obj: Any, cls: Any) -> Union["base[_T]", _T]: ...

      class memo(base[_T]):
          pass

      class C:
          @memo
          def x(self) -> int: ...

      expr = C.x
      #└ TYPE base[int]
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-63737"])
    fun `covariant generic descriptor subclass used as decorator accessed on instance`() = test("""
      from typing import Any, Callable, Generic, TypeVar, Union, overload

      _T_co = TypeVar("_T_co", covariant=True)

      class base(Generic[_T_co]):
          def __init__(self, fget: Callable[..., _T_co]): ...
          @overload
          def __get__(self, obj: None, cls: Any) -> "base[_T_co]": ...
          @overload
          def __get__(self, obj: object, cls: Any) -> _T_co: ...
          def __get__(self, obj: Any, cls: Any) -> Union["base[_T_co]", _T_co]: ...

      class memo(base[_T_co]):
          pass

      class C:
          @memo
          def x(self) -> int: ...

      c = C()
      expr = c.x
      #└ TYPE int
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-26184"])
    fun `generic type from descriptor with type annotation only`() = test("""
      import typing
      from typing import Type, Any

      class MyDescriptor[T]:
          def __get__(self, instance: typing.Any, owner: typing.Any) -> T:
              raise Exception("Not implemented")

      class Test:
          member: MyDescriptor[list]
          def foo(self):
              test = self.member
              expr = test
      #       └ TYPE list
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-26184"])
    fun `generic type from descriptor type annotation has priority`() = test("""
      from typing import Type, Any

      class MyDescriptor[T]:
          def __init__(self, requested_type: Type[T]):
              self.requested_type = requested_type
          def __get__(self, instance: Any, owner: Any) -> T:
              raise Exception("Not implemented")

      class Test:
          member: MyDescriptor[list] = MyDescriptor(str) # WARNING Expected type 'MyDescriptor[list]', got 'MyDescriptor[str]' instead
          def foo(self):
              test = self.member
              expr = test
      #       └ TYPE list
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-26184"])
    fun `generic descriptor access via instance`() = test("""
      from typing import Optional, Any, overload, Union

      class MyDescriptor[T]:
          @overload
          def __get__(self, instance: None, owner: Any) -> str: # access via class
              ...
          @overload
          def __get__(self, instance: object, owner: Any) -> T: # access via instance
              ...
          def __get__(self, instance: Optional[object], owner: Any) -> Union[str, T]:
              ...

      class Foo():
          x = MyDescriptor[int]()

      foo = Foo()
      expr = foo.x
      #└ TYPE int
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-26184"])
    fun `generic descriptor access via instance no matching overloads`() = test("""
      from typing import Any, overload, Union

      class MyDescriptor[T]:
          @overload
          def __get__(self, instance: None, owner: Any) -> T: ...  # access via class
      #       ^^^^^^^ WARNING A series of @overload-decorated methods should always be followed by an implementation that is not @overload-ed
          @overload
          def __get__(self, instance: Bar, owner: Any) -> str | T: ...

      class Foo():
          x = MyDescriptor[int]()

      class Bar(Foo):
          x = MyDescriptor[int]()

      expr = Foo().x
      #└ TYPE Unknown
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-26184"])
    fun `generic descriptor access via instance returns explicit Any`() = test("""
      from typing import Optional, Any, overload, Union

      class MyDescriptor[T]:
          @overload
          def __get__(self, instance: None, owner: Any) -> str: # access via class
              ...
          @overload
          def __get__(self, instance: object, owner: Any) -> Any: # access via instance
              ...
          def __get__(self, instance: Optional[object], owner: Any) -> Union[str, T]:
              ...

      class Foo():
          x = MyDescriptor[int]()

      foo = Foo()
      expr = foo.x
      #└ TYPE Any
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-26184"])
    fun `generic descriptor access via class`() = test("""
      from typing import Optional, Any, overload, Union

      class MyDescriptor[T]:
          @overload
          def __get__(self, instance: None, owner: Any) -> T: # access via class
              ...
          @overload
          def __get__(self, instance: object, owner: Any) -> str: # access via instance
              ...
          def __get__(self, instance: Optional[object], owner: Any) -> Union[str, T]:
              ...

      class Foo():
          x = MyDescriptor[int]()

      expr = Foo.x
      #└ TYPE int
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-26184"])
    fun `generic descriptor access via class returns explicit Any`() = test("""
      from typing import Optional, Any, overload, Union

      class MyDescriptor[T]:
          @overload
          def __get__(self, instance: None, owner: Any) -> Any: # access via class
              ...
          @overload
          def __get__(self, instance: object, owner: Any) -> str: # access via instance
              ...
          def __get__(self, instance: Optional[object], owner: Any) -> Union[str, T]:
              ...

      class Foo():
          x = MyDescriptor[int]()

      expr = Foo.x
      #└ TYPE Any
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-26184"])
    fun `generic descriptor access via class returns nothing`() = test("""
      from typing import Optional, Any, overload, Union

      class MyDescriptor[T]:
          @overload
          def __get__(self, instance: None, owner: Any): # access via class
              ...
          @overload
          def __get__(self, instance: object, owner: Any) -> T: # access via instance
              ...
          def __get__(self, instance: Optional[object], owner: Any) -> Union[str, T]:
              ...

      class Foo():
          x = MyDescriptor[int]()

      expr = Foo.x
      #└ TYPE None
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-26184"])
    fun `generic type from descriptor parameterized on inheritance with type annotation only`() = test("""
      from typing import Any

      class MyDescriptor[T]:
          def __get__(self, instance: Any, owner: Any) -> T:
              ...

      class StrDescriptor(MyDescriptor[str]):
          pass

      class Test:
          member: StrDescriptor

          def foo(self):
              test = self.member
              expr = test
      #       └ TYPE str
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-26184"])
    fun `generic descriptor defined with type annotation in external file access via instance`() = test("""
      from a import Test

      test = Test()
      expr = test.member
      #└ TYPE str
      """.trimIndent(),
      "a.py" to """
        from typing import Optional, Any, overload, Union

        class MyDescriptor[T]:
            @overload
            def __get__(self, instance: None, owner: Any) -> T: # access via class
                ...
            @overload
            def __get__(self, instance: object, owner: Any) -> str: # access via instance
                ...
            def __get__(self, instance: Optional[object], owner: Any) -> Union[str, T]:
                ...

        class Test():
            member: MyDescriptor[int]
        """.trimIndent())

    @Test
    @TestFor(issues = ["PY-11977"])
    @TestCaseOptions(assertRecursionPrevention = false)
    fun `metaclass instance members provided and no type check warning when pass into method use this members`() = test("""
      def expecting(p):
          print(p.meta_inst_lvl)
          print(p.meta_cls_lvl)

      class MyMeta(type):
          meta_cls_lvl = 10

          def __init__(cls, what, bases, dict):
              super().__init__(what, bases, dict)
              cls.meta_inst_lvl = 20

      class MyClass(metaclass=MyMeta):
          pass

      expecting(MyClass)  # check that there is no warnings
      """.trimIndent())
  }

  @Nested
  inner class AttributeAssignment {
    @Test
    fun `instance and class attribute assignment`() = test("""
      from typing import ClassVar

      class ClassAnnotations:
          attr: int
          class_attr: ClassVar[int]

      ClassAnnotations().attr = "foo" # WARNING Expected type 'int', got 'Literal["foo"]' instead
      ClassAnnotations.class_attr = "foo" # WARNING Expected type 'int', got 'Literal["foo"]' instead

      class ClassAnnotationInstanceAssignment:
          attr: int
          def __init__(self, x):
              self.attr = x

      ClassAnnotationInstanceAssignment(42).attr = "foo" # WARNING Expected type 'int', got 'Literal["foo"]' instead

      class InstanceAnnotationAndAssignment:
          def __init__(self):
              self.attr: int = 42

      InstanceAnnotationAndAssignment().attr = "foo" # WARNING Expected type 'int', got 'Literal["foo"]' instead
      """.trimIndent())

    @Test
    fun `class attribute default value type is checked`() = test("""
      from typing import Literal

      class A:
          a: str = "ok"
          b: int = "string" # WARNING Expected type 'int', got 'Literal["string"]' instead
          c: Literal[True] = True
          d: Literal[True] = False # WARNING Expected type 'Literal[True]', got 'Literal[False]' instead

          annotated: int
          annotated = "string" # WARNING Expected type 'int', got 'Literal["string"]' instead
      """.trimIndent())

    @Test
    fun `assigned value matches with dunder set of attribute used in constructor`() = test("""
      class MyDescriptor:
          def __set__(self, obj: object, value: str): ...


      class Test:
          member: MyDescriptor

          def __init__(self, member):
              self.member = member


      x = Test("foo")
      x.member = 42 # WARNING Expected type 'str' (from '__set__'), got 'Literal[42]' instead
      """.trimIndent())

    @Test
    @TestCaseOptions(assertRecursionPrevention = false)
    fun `generic instance variable access via class is ambiguous`() = test("""
      from typing import Self

      class Node[T]:
          x: T

      Node[int].x = 1 # WARNING Access to generic instance variables via class is ambiguous
      Node[int].x # WARNING Access to generic instance variables via class is ambiguous
      Node.x = 1 # WARNING Access to generic instance variables via class is ambiguous
      Node.x # WARNING Access to generic instance variables via class is ambiguous

      p = Node[int]()
      type(p).x # WARNING Access to generic instance variables via class is ambiguous
      i: int = p.x
      j: int = Node[int]().x
      p.x = 1

      class A:
          attr1: list[int]
          attr2: list[Self]
          attr3: Self

      A.attr1
      A.attr2
      A.attr3
      """.trimIndent())

    @Test
    fun `generic instance variable access via class is ambiguous with nested generic`() = test("""
      class Node[T]:
          m: map[str, list[T]]
      #         │^^^^^^^^^^^^ WARNING Passed type arguments do not match type parameters [_S] of class 'map'
      #         └ WARNING Class 'type' does not define '__getitem__', so the '[]' operator cannot be used on its instances

      Node[int].m = {} # WARNING Access to generic instance variables via class is ambiguous
      Node[int].m # WARNING Access to generic instance variables via class is ambiguous
      Node.m # WARNING Access to generic instance variables via class is ambiguous
      Node.m # WARNING Access to generic instance variables via class is ambiguous
      """.trimIndent())

    @Test
    fun `access to attribute of generic class with default is not ambiguous`() = test("""
      class Test1[T = int]():
          attr: T
      class Test2[T]():
          attr: T

      Test1.attr
      Test2.attr # WARNING Access to generic instance variables via class is ambiguous
      """.trimIndent())

    @Test
    fun `generic attribute assignment is checked`() = test("""
      class C[T]:
          attr: list[T]

      c: C[int]
      c.attr = ["foo"] # WARNING FIXME Expected type 'list[int]', got 'list[Literal["foo"]]' instead # PY-91385
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-85974"])
    fun `self attribute assignment`() = test("""
      from typing import Self

      class Node:
          next: Self | None

      c: Node
      c.next = Node()
      """.trimIndent())

    @Test
    fun `augmented assignment to generic attribute`() = test("""
      class A[T]:
          attr: T

      a: A[int] = A()
      a.attr += 1
      a.attr += "s" # WARNING FIXME Expected type 'int', got 'Literal["s"]' instead # PY-91385

      class B:
          def __add__(self, other) -> int: ...

      a: A[B]
      a.attr += 1 # WARNING Expected type 'B' for augmented assignment, got 'int' from operation instead

      class C:
          def __iadd__(self, other) -> int: ...

      a: A[C]
      a.attr += 1 # WARNING Expected type 'C' for augmented assignment, got 'int' from operation instead
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-6426"])
    fun `augmented assignment to descriptor attribute`() = test("""
      class B:
          def __add__(self, other: int) -> int: ...

      class C:
          def __radd__(self, other: B) -> str: ...

      class Desc:
          def __get__(self, instance, owner) -> B: ...
          def __set__(self, instance, value: int) -> None: ...

      class A:
          attr: Desc

      a: A = A()
      a.attr += 1
      a.attr += "s" # WARNING FIXME Expected type 'int', got 'Literal["s"]' instead # PY-91385
      a.attr += C() # WARNING Expected type 'int' (from '__set__'), got 'str' instead
      """.trimIndent())
  }

  @Nested
  inner class DescriptorInUnionType {
    @Test
    @TestFor(issues = ["PY-86411"])
    fun `get applied per member when declared type is union of descriptor and other types`() = test("""
      from typing import Any

      class MyDescriptor:
          def __get__(self, instance: Any, owner: Any) -> str: ...

      class Test:
          desc: MyDescriptor | int | Any
          def foo(self):
              expr = self.desc
      #       └ TYPE str | int | Any
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-86411"])
    fun `get applied to descriptor member leaving plain member untouched`() = test("""
      from typing import Any

      class MyDescriptor:
          def __get__(self, instance: Any, owner: Any) -> str: ...

      class Test:
          desc: MyDescriptor | int
          def foo(self):
              expr = self.desc
      #       └ TYPE str | int
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-86411"])
    fun `generic descriptor member in union binds its type parameter`() = test("""
      from typing import Any

      class MyDescriptor[T]:
          def __get__(self, instance: Any, owner: Any) -> T: ...

      class Test:
          desc: MyDescriptor[bytes] | int
          def foo(self):
              expr = self.desc
      #       └ TYPE bytes | int
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-86411"])
    fun `union without descriptor member is unchanged`() = test("""
      class Test:
          desc: str | int
          def foo(self):
              expr = self.desc
      #       └ TYPE str | int
      """.trimIndent())

    @Test
    @TestFor(issues = ["PY-86411"])
    fun `set expected value type is union of descriptor set value and plain member`() = test("""
      class MyDescriptor:
          def __set__(self, obj: object, value: str): ...

      class Test:
          member: MyDescriptor | int

      t = Test()
      t.member = "foo"
      t.member = 42
      t.member = 1.5 # WARNING Expected type 'str | int' (from '__set__'), got 'float' instead
      """.trimIndent())
  }

  @Test
  @TestFor(issues = ["PY-7340"])
  fun `field initialized with None in another module`() = test(
    """
    from m1 import C

    def f(x: int):
        pass

    def test():
        f(C.foo)
    """.trimIndent(),
    "m1.py" to """
      class C:
          foo = None
      """.trimIndent())

  @Test
  fun `classmethod created via reassignment`() = test(
    """
    class A:
        def foo(cls) -> int:
            return 1

        foo = classmethod(foo)

    foo = A().foo
    #└ TYPE () -> int
    expr = foo()
    #└ TYPE int
    """
  )

  @Test
  @TestFor(issues = ["PY-19412", "PY-90808"])
  fun `classmethod created via reassignment in another module`() = test(
    """
    from a import Spam

    expr = Spam.spam()
    #└ TYPE int
    """.trimIndent(),
    "a.py" to """
      class Spam:
          def spam(cls) -> int:
              return 1

          eggs = False
          spam = classmethod(spam)
      """.trimIndent())
}