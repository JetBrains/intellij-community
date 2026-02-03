"""
Validates the type parameter syntax introduced in PEP 695.
"""

# Specification: https://peps.python.org/pep-0695/#type-parameter-declarations


# This generic class is parameterized by a TypeVar T, a
# TypeVarTuple Ts, and a ParamSpec P.
from typing import Generic, Protocol


class ChildClass[T, *Ts, **P]:
    pass


class ClassA[T](Generic[T]):  # E: Runtime error
    ...


class ClassB[S, T](Protocol):  # OK
    ...


class ClassC[S, T](Protocol[S, T]):  # E
    ...


class ClassD[T: str]:
    def method1(self, x: T):
        x.capitalize()  # OK
        x.is_integer()  # E


class ClassE[T: dict[str, int]]:  # OK
    pass


class ClassF[S: ForwardReference[int], T: "ForwardReference[str]"]:  # OK
    ...


class ClassG[V]:
    class ClassD[T: dict[str, V]]:  # E: generic type not allowed
        ...


class ClassH[T: [str, int]]:  # E: illegal expression form
    ...


class ClassI[AnyStr: (str, bytes)]:  # OK
    ...


class ClassJ[T: (ForwardReference[int], "ForwardReference[str]", bytes)]:  # OK
    ...


class ClassK[T: ()]:  # E: two or more types required
    ...


class ClassL[T: (str,)]:  # E: two or more types required
    ...


t1 = (bytes, str)


class ClassM[T: t1]:  # E: literal tuple expression required
    ...


class ClassN[T: (3, bytes)]:  # E: invalid expression form
    ...


class ClassO[T: (list[S], str)]:  # E: generic type
    ...


class ForwardReference[T]: ...
