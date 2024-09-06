from typing import TypeVar, TypeAlias
T = TypeVar('T', default = int)
U = TypeVar('U', default = str)
StrIntDict: TypeAlias = dict[T, U]