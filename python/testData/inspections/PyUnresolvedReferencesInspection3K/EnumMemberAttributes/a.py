from enum import Enum


class Color(Enum):
    red = 1
    green = 2
    blue = 3


print(Color.red.name, Color.red.name.upper())
print(Color.red.name.<warning descr="Unresolved attribute reference 'foo' for class 'str'">foo</warning>)
print(Color.red.value.<warning descr="Unresolved attribute reference 'foo' for class 'int'">foo</warning>)
print(Color.red.<warning descr="Unresolved attribute reference 'foo' for class 'Literal[Color.red]'">foo</warning>)


print(Color.__members__.items())
print(Color.__members__.<warning descr="Unresolved attribute reference 'foo' for class 'dict'">foo</warning>)
