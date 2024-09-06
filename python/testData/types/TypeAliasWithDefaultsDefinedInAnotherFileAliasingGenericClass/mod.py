from typing import TypeVar, TypeAlias, Generic
T = TypeVar('T')
U = TypeVar('U')
DefaultStrT = TypeVar('DefaultStrT', default = str)
class SomethingWithNoDefaults(Generic[T, T2]): ...
MyAlias: TypeAlias = SomethingWithNoDefaults[int, DefaultStrT]