"""
Tests the compatibility rules between type parameter syntax (introduced in PEP 695)
and traditional TypeVars.
"""

# Specification: https://peps.python.org/pep-0695/#compatibility-with-traditional-typevars

from typing import TypeVar


K = TypeVar("K")


class ClassA[V](dict[K, V]):  # E: traditional TypeVar not allowed here
    ...


class ClassB[K, V](dict[K, V]):  # OK
    ...


class ClassC[V]:
    def method1(self, a: V, b: K) -> V | K:  # OK
        ...

    def method2[M](self, a: M, b: K) -> M | K:  # E
        ...
