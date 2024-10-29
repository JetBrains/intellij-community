from typing import Callable

type A[T, *Ts, **P] = Callable[P, tuple[*Ts, T]]


def f[T, *Ts, **P]() -> Callable[P, tuple[*Ts, T]]:
    ...


class C[T, *Ts, **P]:
    def m(self) -> Callable[P, tuple[*Ts, T]]:
        ...
