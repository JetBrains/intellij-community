import typing


T = typing.TypeVar('T')


class X(typing.Generic[T]):
    pass


class Y(X[T]):
    pass


class Z(Y[T]):
    pass


a: Z[int]
Alias = Z[int]
Z[int]()