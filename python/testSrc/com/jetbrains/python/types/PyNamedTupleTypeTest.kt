// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.types

import com.jetbrains.python.allure.Subsystems
import com.jetbrains.python.allure.Layers
import com.jetbrains.python.allure.Components
import com.intellij.idea.TestFor
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import com.jetbrains.python.psi.LanguageLevel
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Type and type-checker tests for named tuples
 * ([typing.NamedTuple](https://docs.python.org/3/library/typing.html#typing.NamedTuple) in both class and
 * functional form, [collections.namedtuple](https://docs.python.org/3/library/collections.html#collections.namedtuple),
 * their fields, `_make`/`_replace`, unpacking and inheritance)
 */
@Subsystems.Typing
@Components.TypeInference
@Layers.Functional
class PyNamedTupleTypeTest : PyCodeInsightTestCase() {

  @Nested
  inner class CollectionsNamedtuple {

    @Test
    fun `collections namedtuple inheritor field`() = test("""
      from collections import namedtuple
      class User(namedtuple("User", "name age")):
          pass
      expr = User("name", 13).age
      #└ TYPE Unknown
      """)

    @Test
    fun `collections namedtuple target field`() = test("""
      from collections import namedtuple
      User = namedtuple("User", "name age")
      expr = User("name", 13).age
      #└ TYPE Literal[13]
      """)

    @Test
    fun `collections namedtuple inheritor unpacking`() = test("""
      from collections import namedtuple
      class User(namedtuple("User", "name ags")):
          pass
      y1, expr = User("name", 13)
      #   └ TYPE Unknown
      """)

    @Test
    fun `collections namedtuple target unpacking`() = test("""
      from collections import namedtuple
      Point = namedtuple('Point', ['x', 'y'])
      p1 = Point(1, '1')
      expr, y1 = p1
      #└ TYPE Literal[1]
      """)

    @Test
    @TestFor(issues = ["PY-27148"])
    fun `collections namedtuple _make on instance`() = test("""
      from collections import namedtuple
      Cat = namedtuple("Cat", "name age")
      expr = Cat("name", 5)._make(["newname", 6])
      #└ TYPE Cat
      """)

    @Test
    @TestFor(issues = ["PY-27148"])
    fun `collections namedtuple _make on class`() = test("""
      from collections import namedtuple
      Cat = namedtuple("Cat", "name age")
      expr = Cat._make(["newname", 6])
      #└ TYPE Cat
      """)

    @Test
    @TestFor(issues = ["PY-27148"])
    fun `collections namedtuple _make on inheritor instance`() = test("""
      from collections import namedtuple
      class Cat(namedtuple("Cat", "name age")):
          pass
      expr = Cat("name", 5)._make(["newname", 6])
      # └ TYPE Cat
      """)

    @Test
    @TestFor(issues = ["PY-27148"])
    fun `collections namedtuple _make on inheritor class`() = test("""
      from collections import namedtuple
      class Cat(namedtuple("Cat", "name age")):
          pass
      expr = Cat._make(["newname", 6])
      #└ TYPE Cat
      """)

    @Test
    @TestFor(issues = ["PY-27148"])
    fun `collections namedtuple _replace on instance`() = test("""
      from collections import namedtuple
      Cat = namedtuple("Cat", "name age")
      expr = Cat("name", 5)._replace(name="newname")
      #└ TYPE Cat
      """)

    @Test
    @TestFor(issues = ["PY-27148"])
    fun `collections namedtuple _replace on inheritor instance`() = test("""
      from collections import namedtuple
      class Cat(namedtuple("Cat", "name age")):
          pass
      expr = Cat("name", 5)._replace(name="newname")
      #└ TYPE Cat
      """)

    @Test
    @TestFor(issues = ["PY-27148"])
    fun `collections namedtuple _replace result field`() = test("""
      from collections import namedtuple
      Cat = namedtuple("Cat", "name age")
      expr = Cat("name", 5)._replace(age="five").age
      #└ TYPE Literal["five"]
      """)

    @Test
    @TestFor(issues = ["PY-27148"])
    fun `collections namedtuple _replace called unbound`() = test("""
      from collections import namedtuple
      class Cat(namedtuple("Cat", "name age")):
          pass
      expr = Cat._replace(Cat("name", 5), name="newname")
      #└ TYPE Cat
      """)

    @Test
    fun `inherited collections namedtuple _replace`() = test("""
      from collections import namedtuple
      class MyClass(namedtuple('T', 'a b c')):
          def get_foo(self):
              return self.a

      inst = MyClass(1,2,3)
      expr = inst._replace(a=2)
      #└ TYPE MyClass
      """)

    @Test
    fun `namedtuple parameter type in docstring`() = test("""
      from collections import namedtuple
      Point = namedtuple('Point', ('x', 'y'))
      def takes_a_point(point):
          ${"\"\"\""}
          :type point: Point
          ${"\"\"\""}
          expr = point
      #   └ TYPE Point
      """)

    @Test
    fun `no stack overflow on transitive namedtuple fields`() = test("""
      from collections import namedtuple
      class C:
          FIELDS = ('a', 'b')
      FIELDS = C.FIELDS
      expr = namedtuple('Tup', FIELDS)
      #└ TYPE type[tuple]
      """)

    @Test
    fun `function with different namedtuples as parameter and return types`() = test(
      TestOptions(languageLevel = LanguageLevel.PYTHON35, assertRecursionPrevention = false),
      """
      from collections import namedtuple
      MyType1 = namedtuple('MyType1', 'x y')
      MyType2 = namedtuple('MyType2', 'x y')
      def foo(a: MyType1) -> MyType2:
          pass
      expr = foo
      #└ TYPE (a: MyType1) -> MyType2
      """,
    )

    @Test
    fun `iterate over collections namedtuple`() = test("""
      from collections import namedtuple
      Instruction = namedtuple("Instruction", "direction distance")
      def process(instructions: list[Instruction]) -> None:
          for instruction in instructions:
              expr = instruction
      #       └ TYPE Instruction
      """)
  }

  @Nested
  inner class TypingNamedTupleFields {

    @Test
    @TestFor(issues = ["PY-25346"])
    fun `typing NamedTuple class inheritor field`() = test("""
      from typing import NamedTuple
      class User(NamedTuple):
          name: str
          level: int = 0
      expr = User("name").level
      #└ TYPE int
      """)

    @Test
    @TestFor(issues = ["PY-25346"])
    fun `typing NamedTuple functional target field`() = test("""
      from typing import NamedTuple
      User = NamedTuple("User", name=str, level=int)
      expr = User("name").level
      #│                └ WARNING Parameter 'level' unfilled
      #└ TYPE int
      """)

    @Test
    @TestFor(issues = ["PY-32240"])
    fun `typing NamedTuple functional inheritor field`() = test("""
      from typing import NamedTuple

      class A(NamedTuple("NT", [("user", str)])):
          pass

      expr = A(undefined).user
      #│       ^^^^^^^^^ ERROR Unresolved reference 'undefined'
      #└ TYPE str
      """)

    @Test
    @TestFor(issues = ["PY-4351"])
    fun `typing NamedTuple functional inheritor unpacking`() = test("""
      from typing import NamedTuple
      class User(NamedTuple("User", [("name", str), ("age", int)])):
          pass
      y2, expr = User("name", 13)
      #   └ TYPE int
      """)

    @Test
    @TestFor(issues = ["PY-4351"])
    fun `typing NamedTuple functional target unpacking`() = test("""
      from typing import NamedTuple
      Point2 = NamedTuple('Point', [('x', int), ('y', str)])
      p2 = Point2(1, "1")
      expr, y2 = p2
      #└ TYPE int
      """)

    @Test
    @TestFor(issues = ["PY-27148"])
    fun `typing NamedTuple class _make on instance`() = test("""
      from typing import NamedTuple
      class Cat(NamedTuple):
          name: str
          age: int
      expr = Cat("name", 5)._make(["newname", 6])
      #└ TYPE Cat
      """)

    @Test
    @TestFor(issues = ["PY-27148"])
    fun `typing NamedTuple class _make on class`() = test("""
      from typing import NamedTuple
      class Cat(NamedTuple):
          name: str
          age: int
      expr = Cat._make(["newname", 6])
      #└ TYPE Cat
      """)

    @Test
    @TestFor(issues = ["PY-27148"])
    fun `typing NamedTuple functional _make on instance`() = test("""
      from typing import NamedTuple
      Cat = NamedTuple("Cat", name=str, age=int)
      expr = Cat("name", 5)._make(["newname", 6])
      #└ TYPE Cat
      """)

    @Test
    @TestFor(issues = ["PY-27148"])
    fun `typing NamedTuple functional _make on class`() = test("""
      from typing import NamedTuple
      Cat = NamedTuple("Cat", name=str, age=int)
      expr = Cat._make(["newname", 6])
      #└ TYPE Cat
      """)

    @Test
    @TestFor(issues = ["PY-27148"])
    fun `typing NamedTuple class _replace on instance`() = test("""
      from typing import NamedTuple
      class Cat(NamedTuple):
          name: str
          age: int
      expr = Cat("name", 5)._replace(name="newname")
      #└ TYPE Cat
      """)

    @Test
    @TestFor(issues = ["PY-27148"])
    fun `typing NamedTuple functional _replace on instance`() = test("""
      from typing import NamedTuple
      Cat = NamedTuple("Cat", name=str, age=int)
      expr = Cat("name", 5)._replace(name="newname")
      #└ TYPE Cat
      """)

    @Test
    @TestFor(issues = ["PY-27148"])
    fun `typing NamedTuple functional _replace result field`() = test("""
      from typing import NamedTuple
      Cat = NamedTuple("Cat", name=str, age=int)
      expr = Cat("name", 5)._replace(age="give").age
      #│                             ^^^^^^^^^^ WARNING Expected type 'int', got 'Literal["give"]' instead
      #└ TYPE int
      """)

    @Test
    @TestFor(issues = ["PY-27148"])
    fun `typing NamedTuple class _replace called unbound`() = test("""
      from typing import NamedTuple
      class Cat(NamedTuple):
          name: str
          age: int
      expr = Cat._replace(Cat("name", 5), name="newname")
      #└ TYPE Cat
      """)

    @Test
    fun `typing NamedTuple class subscription field`() = test("""
      from typing import NamedTuple

      class Point(NamedTuple):
          x: int
          y: int

      def foo(point: Point):
          expr = point[1]
      #   └ TYPE int
      """)
  }

  @Nested
  inner class TypingNamedTupleInspections {

    @Test
    fun `typing NamedTuple functional as parameter`() = test("""
      from typing import NamedTuple


      nt = NamedTuple("name", [("field", str)])


      def foo(x: nt):
          pass


      foo(5) # WARNING Expected type 'name', got 'Literal[5]' instead
      foo(nt(field = "f"))
      """)

    @Test
    @TestFor(issues = ["PY-23239", "PY-23253"])
    fun `initializing typing NamedTuple`() = test("""
      import typing
      from typing import List


      MyTup2 = typing.NamedTuple("MyTup2", bar=int, baz=str)
      MyTup3 = typing.NamedTuple("MyTup2", [("bar", int), ("baz", str)])


      class MyTup4(typing.NamedTuple):
          bar: int
          baz: str


      class MyTup5(typing.NamedTuple):
          bar: int
          baz: str
          foo = 5


      class MyTup6(typing.NamedTuple):
          bar: int
          baz: str
          foo: int


      MyTup7 = typing.NamedTuple("MyTup7", names=List[str], ages=List[int])


      # fail
      MyTup2('', '') # WARNING Expected type 'int', got 'Literal[""]' instead
      MyTup2(bar='', baz='') # WARNING Expected type 'int', got 'Literal[""]' instead
      MyTup2(baz='', bar='') # WARNING Expected type 'int', got 'Literal[""]' instead


      # ok
      MyTup2(5, '')
      MyTup2(bar=5, baz='')
      MyTup2(baz='', bar=5)


      # fail
      MyTup3('', '') # WARNING Expected type 'int', got 'Literal[""]' instead
      MyTup3(bar='', baz='') # WARNING Expected type 'int', got 'Literal[""]' instead
      MyTup3(baz='', bar='') # WARNING Expected type 'int', got 'Literal[""]' instead


      # ok
      MyTup3(5, '')
      MyTup3(bar=5, baz='')
      MyTup3(baz='', bar=5)


      # fail
      MyTup4('', '') # WARNING Expected type 'int', got 'Literal[""]' instead
      MyTup4(bar='', baz='') # WARNING Expected type 'int', got 'Literal[""]' instead
      MyTup4(baz='', bar='') # WARNING Expected type 'int', got 'Literal[""]' instead


      # ok
      MyTup4(5, '')
      MyTup4(bar=5, baz='')
      MyTup4(baz='', bar=5)


      # fail
      MyTup5('', '') # WARNING Expected type 'int', got 'Literal[""]' instead
      MyTup5(bar='', baz='') # WARNING Expected type 'int', got 'Literal[""]' instead
      MyTup5(baz='', bar='') # WARNING Expected type 'int', got 'Literal[""]' instead


      # ok
      MyTup5(5, '')
      MyTup5(bar=5, baz='')
      MyTup5(baz='', bar=5)


      # fail
      MyTup6(bar='', baz='', foo='')
      #      │               ^^^^^^ WARNING Expected type 'int', got 'Literal[""]' instead
      #      ^^^^^^ WARNING Expected type 'int', got 'Literal[""]' instead
      MyTup6('', '', '')
      #      │       ^^ WARNING Expected type 'int', got 'Literal[""]' instead
      #      ^^ WARNING Expected type 'int', got 'Literal[""]' instead


      # ok
      MyTup6(bar=5, baz='', foo=5)
      MyTup6(5, '', 5)


      # fail
      MyTup7(names="A", ages=5)
      #      │          ^^^^^^ WARNING Expected type 'list[int]', got 'Literal[5]' instead
      #      ^^^^^^^^^ WARNING Expected type 'list[str]', got 'Literal["A"]' instead
      MyTup7("A", 5)
      #      │    └ WARNING Expected type 'list[int]', got 'Literal[5]' instead
      #      ^^^ WARNING Expected type 'list[str]', got 'Literal["A"]' instead


      # ok
      MyTup7(names=["A"], ages=[5])
      MyTup7(["A"], [5])
      """)

    @Test
    @TestFor(issues = ["PY-76845"])
    fun `NamedTuple compatible with tuple`() = test("""
      from typing import NamedTuple

      class NT(NamedTuple):
          a: str
          b: int

      x: tuple[str, int] = NT("a", 1)
      y: tuple[str, int, str] = NT("a", 1) # WARNING Expected type 'tuple[str, int, str]', got 'NT' instead
      """)
  }
}
