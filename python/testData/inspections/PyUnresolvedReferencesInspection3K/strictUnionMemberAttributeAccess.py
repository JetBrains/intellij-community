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
    x.<weak_warning descr="Member 'None' of 'A | None' does not have attribute 'method'">method</weak_warning>()

def union_with_all_incompatible_types(x: object | None):
    x.<weak_warning descr="Member 'object' of 'object | None' does not have attribute 'method'">method</weak_warning>()

def union_with_some_incompatible_types_and_any(x: Any | None):
    x.<weak_warning descr="Member 'None' of 'Any | None' does not have attribute 'method'">method</weak_warning>()

def narrowing_union_with_some_incompatible_types_after(x: Any | None):
    if isinstance(x, A):
        x.method()
    assert isinstance(x, B)
    x.method()