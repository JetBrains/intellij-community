import mod
from typing import Protocol, TypeVar

T1 = TypeVar("T1")
T2 = TypeVar("T2")
T3 = TypeVar("T3")


class Options1(Protocol[T1, T2, T3]):
    timeout: T1
    one_flag: bool
    other_flag: bool

    def foo(self, x: T1, y: T2) -> T3: ...

t1: Options1[int, str, bool] = mod
t2: Options1[int, float, bool] = <warning descr="Expected type 'Options1[int, float, bool]', got 'mod.py' instead">mod</warning>
t3: Options1[str, float, bool] = <warning descr="Expected type 'Options1[str, float, bool]', got 'mod.py' instead">mod</warning>
t4: Options1[int, str, str] = <warning descr="Expected type 'Options1[int, str, str]', got 'mod.py' instead">mod</warning>