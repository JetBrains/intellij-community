from typing import TypeVar


T = TypeVar('T')


def func(xs: <warning descr="Builtin 'list' cannot be parameterized directly">li<caret>st[<warning descr="Builtin 'tuple' cannot be parameterized directly">tuple[int, str]</warning>]</warning>, y: <warning descr="Builtin 'type' cannot be parameterized directly">type[T]</warning>):
    pass
