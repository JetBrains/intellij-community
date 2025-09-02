"""
Tests the handling of unpacked tuples embedded within other tuples.
"""

# Specification: https://typing.readthedocs.io/en/latest/spec/tuples.html#unpacked-tuple-form

# > An unpacked form of ``tuple`` (using an unpack operator ``*``)
# > can be used within a tuple type argument list

# > Unpacking a concrete tuple type is analogous to unpacking a tuple of values at runtime.


from typing import TypeVarTuple, Unpack, assert_type


def func1(x: tuple[int, *tuple[bool, bool], str]):
    assert_type(x, tuple[int, bool, bool, str])
    assert_type(x, tuple[*tuple[int, bool], bool, str])
    assert_type(x, tuple[int, bool, *tuple[bool, str]])


# > Unpacking an unbounded tuple preserves the unbounded tuple as it is.


def func2(x: tuple[int, *tuple[bool, ...], str]):
    assert_type(x, tuple[int, *tuple[bool, ...], str])


# > For example, tuple[int, *tuple[str]] is equivalent to tuple[int, str].

u1: tuple[*tuple[int], *tuple[int]] = (int(1), int(1))  # OK
assert_type(u1, tuple[int, int])
u2: tuple[*tuple[int, ...], *tuple[int]]  # OK


# > Only one unbounded tuple can be used within another tuple:

t1: tuple[*tuple[str], *tuple[str]]  # OK
t2: tuple[*tuple[str, *tuple[str, ...]]]  # OK
t3: tuple[*tuple[str, ...], *tuple[int, ...]]  # E
t4: tuple[*tuple[str, *tuple[str, ...]], *tuple[int, ...]]  # E


# > An unpacked TypeVarTuple counts as an unbounded tuple in the context of this rule

Ts = TypeVarTuple("Ts")


def func3(t: tuple[*Ts]):
    t5: tuple[*tuple[str], *Ts]  # OK
    t6: tuple[*tuple[str, ...], *Ts]  # E


# > The ``*`` syntax requires Python 3.11 or newer. For older versions of Python,
# > the ``typing.Unpack`` special form can be used:

t11: tuple[Unpack[tuple[str]], Unpack[tuple[str]]]  # OK
t12: tuple[Unpack[tuple[str, Unpack[tuple[str, ...]]]]]  # OK
t13: tuple[Unpack[tuple[str, ...]], Unpack[tuple[int, ...]]]  # E
t14: tuple[  # E[t14]
    Unpack[tuple[str, Unpack[tuple[str, ...]]]], Unpack[tuple[int, ...]]  # E[t14]
]
