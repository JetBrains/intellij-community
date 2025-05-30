from typing import Any


class A:
    def method(self):
        pass

class B(A):
    pass

class C:
    def method(self):
        pass

def union_with_all_compatible_types(x: A | B | C):
    x.method()

def union_with_some_incompatible_types(x: A | None):
    x.<weak_warning descr="Some members of 'A | None' don't have attribute 'method'">method</weak_warning>()

def union_with_all_incompatible_types(x: object | None):
    x.<warning descr="Cannot find reference 'method' in 'object | None'">method</warning>()

def union_with_some_incompatible_types_and_any(x: Any | None):
    x.<weak_warning descr="Some members of 'Any | None' don't have attribute 'method'">method</weak_warning>()

def narrowing_union_with_some_incompatible_types_after(x: Any | None):
    if isinstance(x, A):
        x.method()
    assert isinstance(x, B)
    x.method()