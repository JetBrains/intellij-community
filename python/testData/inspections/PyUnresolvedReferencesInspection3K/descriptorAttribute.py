from typing import Any


class StringDescriptor:
    def __get__(self, instance, owner):
        return 'foo'


class AnyDescriptor:
    def __get__(self, instance, owner) -> Any:
        return 'bar'


class ListDescriptor:
    def __get__(self, instance: Any, owner: Any) -> list:
        return 'baz'


class C:
    foo = StringDescriptor()
    bar = AnyDescriptor()
    baz = ListDescriptor()


# Instance level
c = C()
c.foo.upper()
c.foo.<warning descr="Unresolved attribute reference 'non_existent' for class 'str'">non_existent</warning>()
c.bar.upper()
c.bar.non_existent()
c.baz.append()
c.baz.<warning descr="Unresolved attribute reference 'non_existent' for class 'list'">non_existent</warning>()


# Class level
C.foo.upper()
C.foo.<warning descr="Unresolved attribute reference 'non_existent' for class 'str'">non_existent</warning>()
C.bar.upper()
C.bar.non_existent()
