from dataclasses import dataclass
from typing import NamedTuple
from enum import Enum

class A:
    x = 1

    @classmethod
    def foo(cls):
        pass

    @staticmethod
    def bar():
        pass

A.x = 2
A.foo = lambda: None
A.bar = lambda: None
A.<warning descr="Unresolved attribute reference 'y' for class 'A'">y</warning> = 1


class Parent:
    x = 1


class Child(Parent):
    pass


Child.x = 2
Child.<warning descr="Unresolved attribute reference 'z' for class 'Child'">z</warning> = 1


# __getattr__ overridden
class WithGetattr:
    def __getattr__(self, name):
        pass


WithGetattr.whatever = 1

# Instance assignment — not affected by this check
a = A()
a.new_attr = 1


@dataclass
class Point:
    x: int
    y: int


Point.x = 0
Point.y = 0
Point.<warning descr="Unresolved attribute reference 'z' for class 'Point'">z</warning> = 0


# NamedTuple — fields should be known
class Point(NamedTuple):
    x: float
    y: float


Point.x = 0.0
Point.y = 0.0
Point.<warning descr="Unresolved attribute reference 'z' for class 'Point'">z</warning> = 0.0


class Color(Enum):
    RED = 1
    GREEN = 2


Color.RED = 1
Color.<warning descr="Unresolved attribute reference 'YELLOW' for class 'Color'">YELLOW</warning> = 3


# Nested class — inner class is an attribute
class Outer:
    class Inner:
        pass


Outer.Inner = type('Replacement', (), {})


# __slots__ class — slot names are valid attributes
class Slotted:
    __slots__ = ('a', 'b')

Slotted.a = 1
Slotted.b = 1
Slotted.<warning descr="Unresolved attribute reference 'c' for class 'Slotted'">c</warning> = 1


# Property — property name is a known attribute
class WithProperty:
    @property
    def value(self):
        return 42


WithProperty.value = 0


# __init__ instance attribute — not a class attribute
class WithInit:
    def __init__(self):
        self.instance_var = 1


WithInit.<warning descr="Unresolved attribute reference 'instance_var' for class 'WithInit'">instance_var</warning> = 2


# Multiple inheritance — attribute from any parent is OK
class Mixin:
    extra = 10


class Combined(Parent, Mixin):
    pass


Combined.x = 1
Combined.extra = 20
Combined.<warning descr="Unresolved attribute reference 'missing' for class 'Combined'">missing</warning> = 0
