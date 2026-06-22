// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.types

import com.intellij.idea.TestFor
import com.jetbrains.python.fixtures.PyCodeInsightTestCase
import com.jetbrains.python.psi.LanguageLevel
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Type and type-checker tests for [enum][https://docs.python.org/3/library/enum.html] members,
 * values, aliases and narrowing.
 */
class PyEnumTypeTest : PyCodeInsightTestCase() {

  @Nested
  inner class MemberLiteralsAndDefinitions {

    @Test
    @TestFor(issues = ["PY-76816"])
    fun `member of enum defined via Enum subclass`() = test("""
      from enum import Enum
      
      class CustomEnum(Enum):
          pass
      
      class Color(CustomEnum):
          RED = 1
      
      expr = Color.RED
      # └ TYPE Literal[Color.RED]
      """)

    @Test
    @TestFor(issues = ["PY-76816"])
    fun `member of enum defined via EnumType metaclass`() = test("""
      from enum import EnumType
      
      class CustomEnumType(EnumType):
          pass
      
      class CustomEnum(metaclass=CustomEnumType):
          pass
      
      class Color(CustomEnum):
          RED = 1
      
      expr = Color.RED
      # └ TYPE Literal[Color.RED]
      """)

    @Test
    fun `enum members of various kinds`() = test("""
      from enum import Enum
      
      def func(x: int) -> None: ...
      
      val = 2
      
      class Example(Enum):
          foo = lambda: 1
          bar = staticmethod(func)
          A = 1
          B = val
      
      expr = Example.foo, Example.bar, Example.A, Example.B
      # └ TYPE tuple[() -> Literal[1], (x: int) -> None, Literal[Example.A], Literal[Example.B]]
      """)

    @Test
    fun `enum member and nonmember`() = test(TestOptions(assertRecursionPrevention = false), """
      from enum import Enum, member, nonmember
      
      def func(x: int) -> None: ...
      
      class Example(Enum):
          a = nonmember(1)
          A = member(lambda: 1)
          B = member(staticmethod(func))
      
          @member
          def method() -> None: ...
      
      expr = Example.a, Example.A, Example.B, Example.method
      # └ TYPE tuple[int, Literal[Example.A], Literal[Example.B], Literal[Example.method]]
      """)

    @Test
    fun `enum member and nonmember from another file`() = test(
      TestOptions(assertRecursionPrevention = false),
      """
      from enum_members import Example
      
      expr = Example.a, Example.A, Example.B, Example.method
      #└ TYPE tuple[int, Literal[Example.A], Literal[Example.B], Literal[Example.method]]
      """,
      "enum_members.py" to """
        from enum import Enum, member, nonmember
        from _enum_members import *
        
        class Example(Enum):
            a = nonmember(1)
            A = member(lambda: 1)
            B = member(staticmethod(func))
        
            @member
            def method() -> None: ...
        """,
      "_enum_members.py" to "def func(x: int) -> None: ...",
    )
  }

  @Nested
  inner class Auto {

    @Test
    fun `enum auto values yield member literals`() = test("""
      from enum import Enum, auto
      
      class Color(Enum):
          RED = auto()
          BLUE = auto()
      
      expr = Color.RED, Color.BLUE
      # └ TYPE tuple[Literal[Color.RED], Literal[Color.BLUE]]
      """)

    @Test
    fun `enum auto values yield member literals from another file`() = test(
      """
      from color import Color
      
      expr = Color.RED, Color.BLUE
      #└ TYPE tuple[Literal[Color.RED], Literal[Color.BLUE]]
      """,
      "color.py" to """
        from enum import Enum, auto
        
        
        class Color(Enum):
            RED = auto()
            BLUE = auto()
        """,
    )
  }

  @Nested
  inner class MemberAliases {

    @Test
    fun `enum member aliases resolve to the original member`() = test("""
      from enum import EnumMeta, member
      
      class Color(metaclass=EnumMeta):
          RED = 1
      
          R = RED
          r = R
      
      expr = Color.RED, Color.R, Color.r
      #└ TYPE tuple[Literal[Color.RED], Literal[Color.RED], Literal[Color.RED]]
      """)

    @Test
    fun `enum member function aliases resolve to the original member`() = test("""
      from enum import EnumMeta, member
      
      class Color(metaclass=EnumMeta):
          @member
          def foo(x: int) -> int:
              pass
      
          bar = foo
          buz = bar
      
      expr = Color.foo, Color.bar, Color.buz
      #└ TYPE tuple[Literal[Color.foo], Literal[Color.foo], Literal[Color.foo]]
      """)

    @Test
    fun `enum member aliases resolve to the original member from another file`() = test(
      """
      from color import *
      expr = Color.RED, Color.R, Color.r, Color.foo, Color.bar, Color.buz
      #└ TYPE tuple[Literal[Color.RED], Literal[Color.RED], Literal[Color.RED], Literal[Color.foo], Literal[Color.foo], Literal[Color.foo]]
      """,
      "color.py" to """
        from enum import EnumMeta, member
        
        class Color(metaclass=EnumMeta):
            RED = 1
        
            R = RED
            r = R
        
            @member
            def foo(x: int) -> int:
                pass
        
            bar = foo
            buz = bar
        """,
    )
  }

  @Nested
  inner class ValueType {

    @Test
    @TestFor(issues = ["PY-55734"])
    fun `IntEnum value type`() = test("""
      from enum import IntEnum, auto
      
      class State(IntEnum):
          A = auto()
          B = auto()
      
      def foo(arg: State):
          expr = arg.value
      #   └ TYPE int
      """)

    @Test
    @TestFor(issues = ["PY-79330"])
    fun `enum auto value type`() = test("""
      from enum import auto, Enum
      
      class MyEnum(Enum):
          FOO = auto()
          BAR = FOO
      
      def foo(e: MyEnum):
          expr = e.value
      #   └ TYPE int
      """)

    @Test
    @TestFor(issues = ["PY-79330"])
    fun `enum auto value type with custom _generate_next_value_`() = test("""
      from enum import auto, Enum
      
      class MyEnumBase(Enum):
          @staticmethod
          def _generate_next_value_(name: str, start: int, count: int, last_values: list[str]) -> str: ...
      
      class MyEnumDerived(MyEnumBase):
          FOO = auto()
      
      def foo(e: MyEnumDerived):
          expr = e.value
      #   └ TYPE str
      """)

    @Test
    @TestFor(issues = ["PY-79330"])
    fun `enum auto value type with custom _generate_next_value_ from another file`() = test(
      """
      from my_enum import MyEnumDerived
      
      def foo(e: MyEnumDerived):
          expr = e.value
      #   └ TYPE str
      """,
      "my_enum.py" to """
        from enum import auto, Enum
        
        
        class MyEnumBase(Enum):
            @staticmethod
            def _generate_next_value_(name: str, start: int, count: int, last_values: list[str]) -> str: ...
        
        
        class MyEnumDerived(MyEnumBase):
            FOO = auto()
        """,
    )

    @Test
    @TestFor(issues = ["PY-16622"])
    fun `value type of enum member stored in a variable`() = test("""
      from enum import Enum
      
      
      class IDE(Enum):
          DS = 'DataSpell'
          PY = 'PyCharm'
      
      
      IDE_TO_CLEAR_SETTINGS_FOR = IDE.PY
      expr = IDE_TO_CLEAR_SETTINGS_FOR.value
      # └ TYPE Literal["PyCharm"]
      """)

    @Test
    @TestFor(issues = ["PY-16622"])
    fun `value type of enum member returned from a function`() = test("""
      from enum import Enum
      
      class Fruit(Enum):
          Apple = 1
          Banana = 2
      
      def f():
          return Fruit.Apple
      
      res = f()
      expr = res.value
      #└ TYPE Literal[1]
      """)

    @Test
    @TestFor(issues = ["PY-54503"])
    fun `value type of enum item from __getitem__`() = test("""
      import enum
      
      class MyEnum(enum.Enum):
          ONE = 1
          TWO = 2
      
      expr = MyEnum['ONE'].value
      # └ TYPE Literal[1, 2]
      """)

    @Test
    @TestFor(issues = ["PY-54503"])
    fun `value type of enum item from __call__`() = test(TestOptions(assertRecursionPrevention = false), """
      import enum
      
      class MyEnum(enum.Enum):
          ONE = 1
          TWO = 2
      
      expr = MyEnum(1).value
      #└ TYPE Literal[1, 2]
      """)

    @Test
    @TestFor(issues = ["PY-54503"])
    fun `value type of type-hinted enum item`() = test("""
      import enum
      
      class MyEnum(enum.Enum):
          ONE = 1
          TWO = 2
      
      def f(p: MyEnum):
          expr = p.value
      #   └ TYPE Literal[1, 2]
      """)

    @Test
    @TestFor(issues = ["PY-79330", "PY-71603"])
    fun `value type of bare Enum parameter`() = test("""
      from enum import Enum
      
      def f(p: Enum):
          expr = p.value
      #   └ TYPE Any
      """)

    @Test
    @TestFor(issues = ["PY-54503"])
    fun `value type of imported enum item from __getitem__`() = test(
      """
      from mod import MyEnum
      
      expr = MyEnum['ONE'].value
      # └ TYPE Literal[1, 2]
      """,
      "mod.py" to """
        import enum
        
        
        class MyEnum(enum.Enum):
            ONE = 1
            TWO = 2
        """,
    )

    @Test
    @TestFor(issues = ["PY-57621"])
    fun `value type of enum member with a tuple value`() = test("""
      from enum import Enum
      
      class Color(Enum):
        RED = 1, "red"
        BLUE = 2, "blue"
      
      expr = Color.BLUE.value
      # └ TYPE tuple[Literal[2], Literal["blue"]]
      """)
  }

  @Nested
  inner class SpecialEnumKinds {

    @Test
    fun `Flag member is not expanded after narrowing`() = test("""
      from enum import Flag
      
      class MyFlag(Flag):
          FLAG1 = 1
          FLAG2 = 2
      
      def foo(f: MyFlag):
          if f is MyFlag.FLAG1:
              pass
          else:
              expr = f
      #       └ TYPE MyFlag
      """)

    @Test
    @TestFor(issues = ["PY-87344"])
    fun `set of StrEnum class inferred from values classmethod`() = test(TestOptions(enablePyAnyType = false), """
      from enum import StrEnum
      from typing import Self
      
      class Variant(StrEnum):
          CREATED = "created"
      
          @classmethod
          def values(cls) -> set[Self]:
              return set(cls)
      
      expr = set(Variant)
      # └ TYPE set[Variant]
      """)

    @Test
    @TestFor(issues = ["PY-87344"])
    fun `set of StrEnum via cls`() = test(TestOptions(enablePyAnyType = false), """
      from enum import StrEnum
      from typing import Self
      
      class Variant(StrEnum):
          CREATED = "created"
      
          @classmethod
          def values(cls):
              expr = set(cls)
      #       └ TYPE set[Self@Variant]
      """)
  }

  @Nested
  inner class Narrowing {

    @Test
    fun `is enum member narrowing with or`() = test("""
      from enum import Enum
      
      class Answer(Enum):
          Yes = 1
          No = 2
      
      def foo(v: object):
          if v is Answer.Yes or v is Answer.No:
              expr = v
      #       └ TYPE Literal[Answer.Yes, Answer.No]
      """)

    @Test
    fun `is enum member narrowing with negated and`() = test("""
      from enum import Enum
      
      class Answer(Enum):
          Yes = 1
          No = 2
      
      def foo(v: object):
          if v is not Answer.Yes and v is not Answer.No:
              raise ValueError("Invalid value")
          expr = v
      #   └ TYPE Literal[Answer.Yes, Answer.No]
      """)

    @Test
    fun `is enum member narrowing with assert`() = test("""
      from enum import Enum
      
      class Answer(Enum):
          Yes = 1
          No = 2
      
      def foo(v: object):
          assert v is Answer.Yes or v is Answer.No
          expr = v
      #   └ TYPE Literal[Answer.Yes, Answer.No]
      """)

    @Test
    fun `union with enum members narrowed by isinstance`() = test("""
      from enum import Enum
      
      class Color(Enum):
          R = 1
          G = 2
          B = 3
      
      def f(v: str | Color):
          if isinstance(v, str):
              pass
          else:
              expr = v
      #       └ TYPE Color
      """)
  }

  @Nested
  inner class IterationAndIndexing {

    @Test
    @TestFor(issues = ["PY-36205"])
    fun `iterate enum yields members`() = test("""
      from enum import Enum
      class Foo(str, Enum):
          ONE = 1 # WARNING Expected type 'str', got 'Literal[1]' instead
      for expr in Foo:
      #   └ TYPE Foo
          pass
      """)

    @Test
    @TestFor(issues = ["PY-77074"])
    fun `enum indexer yields enum instance`() = test("""
      from enum import Enum
      
      class Color(Enum):
        RED = 1
      
      expr = Color["RED"]
      #└ TYPE Color
      """)
  }

  @Nested
  inner class LiteralTypeAssignability {

    @Test
    fun `typing Literal of enum member`() = test(
      TestOptions(languageLevel = LanguageLevel.PYTHON35),
      """
      from typing_extensions import Literal
      
      from enum import Enum
      
      class A(Enum):
          V1 = 1
          V2 = 2
      
      expr: Literal[A.V1] = A.V1 # ERROR Python version 3.5 does not support variable annotations
      #└ TYPE Literal[A.V1]
      """,
    )

    @Test
    @TestFor(issues = ["PY-46385"])
    fun `aliasing enum class name in literal type`() = test("""
      from enum import Enum
      from typing import Literal
      
      class Colors(Enum):
          RED = 1
          GREEN = 1
          BLUE = 3
      
      AliasColors = Colors
      
      x: AliasColors = Colors.RED
      y: Literal[Colors.RED] = Colors.GREEN # WARNING Expected type 'Literal[Colors.RED]', got 'Literal[Colors.GREEN]' instead
      z: Literal[AliasColors.RED] = Colors.RED
      """)

    @Test
    @TestFor(issues = ["PY-46385"])
    fun `aliasing enum member name in literal type`() = test("""
      from enum import Enum
      from typing import Literal
      
      class Colors(Enum):
          RED = 1
          GREEN = 2
          BLUE = 3
      
      SpecialColors = Literal[Colors.RED]
      
      def special_painter(color: SpecialColors):
          assert color == Colors.RED
      
      special_painter(Colors.GREEN) # WARNING Expected type 'Literal[Colors.RED]', got 'Literal[Colors.GREEN]' instead
      
      costs: dict[SpecialColors, int] = {Colors.GREEN: 7} # WARNING Expected type 'dict[Literal[Colors.RED], int]', got 'dict[Literal[Colors.GREEN], Literal[7]]' instead
      """)

    @Test
    fun `enum member alias assignable to literal type`() = test("""
      from enum import Enum
      from typing import Literal
      
      class Color(Enum):
          RED = 1
          R = RED
      
      x: Literal[Color.RED]
      x = Color.R
      """)

    @Test
    @TestFor(issues = ["PY-80195"])
    fun `multi value enum literal assignability across files`() = test(
      """
      from typing import Literal
      from m import SimpleEnum, SuperEnum
      
      p: Literal[SuperEnum.PINK] = SuperEnum.PINK
      q: Literal[SimpleEnum.FOO] = SuperEnum.PINK # WARNING Expected type 'Literal[SimpleEnum.FOO]', got 'Literal[SuperEnum.PINK]' instead
      """,
      "m.py" to """
        import enum
        
        
        class SuperEnum(enum.Enum):
            PINK = "PINK", "hot"
            FLOSS = "FLOSS", "sweet"
        
        
        class SimpleEnum(enum.Enum):
            FOO = "FOO"
        """,
    )
  }

  @Nested
  inner class InspectionsOnMembersAndValues {

    @Test
    @TestFor(issues = ["PY-77937"])
    fun `list of enum members is homogeneous`() = test("""
      from enum import Enum
      
      class Direction(Enum):
          NORTH = "N"
          SOUTH = "S"
          EAST = "E"
          WEST = "W"
          LEFT = "L"
          RIGHT = "R"
          FORWARD = "F"
      
      CARTESIAN = [Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST]
      
      def index(d: Direction) -> None:
          print(CARTESIAN.index(d))
      """)

    @Test
    @TestFor(issues = ["PY-80837"])
    fun `enum attribute default value type is checked`() = test("""
      from enum import Enum, IntEnum

      class MyIntEnum(IntEnum):
          OK = 1
          BAD = "string"
      #         ^^^^^^^^ WARNING Expected type 'int', got 'Literal["string"]' instead
      #         ^^^^^^^^ WARNING Type 'Literal["string"]' is not assignable to declared type 'int'

      class MyEnum(Enum):
          OK = 1
          BAD = "string" # WARNING Expected type 'int', got 'str' instead
      """)

    @Test
    @TestFor(issues = ["PY-88892"])
    fun `plain enum member declared as a tuple is validated as the whole value`() = test("""
      from enum import Enum, StrEnum, IntEnum

      # Plain stdlib enums have no metaclass/constructor that consumes extra elements: the whole tuple is the value,
      # so a str/int-based enum rejects it (runtime TypeError). Only framework enums (e.g. django Choices) relax this
      # via PyEnumMemberDeclarationProvider; see DjangoEnumTypeTest.
      class PlainStr(StrEnum):
          A = "A", "A"   # WARNING Expected type 'str', got 'tuple[Literal["A"], Literal["A"]]' instead

      class PlainInt(IntEnum):
          B = 1, "label" # WARNING Expected type 'int', got 'tuple[Literal[1], Literal["label"]]' instead

      class PlainEnum(Enum):
          C = 1, 2       # OK: value type is inferred as the tuple itself
      """)

    @Test
    @TestFor(issues = ["PY-87344"])
    fun `iterating an Enum type and instance`() = test(TestOptions(enablePyAnyType = false), """
      from enum import Enum
      from typing import Self
      
      class Color(Enum):
          RED = "red"
      
          @classmethod
          def all(cls) -> set[Self]:
              return set(cls)
      
          def foo(self):
              # __iter__ is defined in EnumMeta, thus, for definitions only
              return set(self) # WARNING Expected type 'Iterable[Any]' (matched generic type 'Iterable[_T]'), got 'Self@Color' instead
      """)

    @Test
    @TestFor(issues = ["PY-87344"])
    fun `iterating a StrEnum type and instance`() = test(TestOptions(enablePyAnyType = false), """
      from enum import StrEnum
      from typing import Self
      
      
      class Variant(StrEnum):
          CREATED = "created"
      
          @classmethod
          def values(cls) -> set[Self]:
              return set(cls)
      
          def foo(self):
              # StrEnum inherits str which inherits Iterable[str], thus, iterable for both instance and definition
              return set(self) # OK
      """)
  }
}
