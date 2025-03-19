"""
Tests for the use of TypeVarTuple with "*args" parameter.
"""

# Specification: https://typing.readthedocs.io/en/latest/spec/generics.html#args-as-a-type-variable-tuple

# > If *args is annotated as a type variable tuple, the types of the individual
# > arguments become the types in the type variable tuple.

from typing import TypeVarTuple, assert_type


Ts = TypeVarTuple("Ts")


def args_to_tuple(*args: *Ts) -> tuple[*Ts]:
    ...


assert_type(args_to_tuple(1, "a"), tuple[int, str])


class Env:
    ...


def exec_le(path: str, *args: * tuple[*Ts, Env], env: Env | None = None) -> tuple[*Ts]:
    ...


assert_type(exec_le("", Env()), tuple[()])  # OK
assert_type(exec_le("", 0, "", Env()), tuple[int, str])  # OK
exec_le("", 0, "")  # E
exec_le("", 0, "", env=Env())  # E


# > Using an unpacked unbounded tuple is equivalent to the
# > PEP 484#arbitrary-argument-lists-and-default-argument-values behavior of
# > *args: int, which accepts zero or more values of type int.


def func1(*args: * tuple[int, ...]) -> None:
    ...


func1()  # OK
func1(1, 2, 3, 4, 5)  # OK
func1(1, "2", 3)  # E


def func2(*args: * tuple[int, *tuple[str, ...], str]) -> None:
    ...


func2(1, "")  # OK
func2(1, "", "", "", "")  # OK
func2(1, 1, "")  # E
func2(1)  # E
func2("")  # E


def func3(*args: * tuple[int, str]) -> None:
    ...


func3(1, "hello")  # OK
func3(1)  # E


def func4(*args: tuple[*Ts]):
    ...


func4((0,), (1,))  # OK
func4((0,), (1, 2))  # E
func4((0,), ("1",))  # E


# This is a syntax error, so leave it commented out.
# def func5(**kwargs: *Ts): # E
#     ...
