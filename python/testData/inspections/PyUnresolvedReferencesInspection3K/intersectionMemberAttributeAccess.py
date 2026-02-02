from typing import Any


class A:
    def method(self):
        pass


class B:
    def method(self):
        pass


# Using string annotations to suppress the warnings about unresolved '__and__' in type
def intersection_with_all_compatible_types(x: 'A <warning descr="Class 'type' does not define '__and__', so the '&' operator cannot be used on its instances">&</warning> B'):
    x.method()

def intersection_with_some_incompatible_types(x: 'A <warning descr="Class 'type' does not define '__and__', so the '&' operator cannot be used on its instances">&</warning> None'):
    x.method()

def intersection_with_all_incompatible_types(x: 'object <warning descr="Class 'type' does not define '__and__', so the '&' operator cannot be used on its instances">&</warning> None'):
    x.<warning descr="Cannot find reference 'method' in 'object & None'">method</warning>()

def intersection_with_incompatible_types_and_any(x: 'Any & None'):
    x.method()
