
from typing import Union, MutableMapping

_KeyType = Union[str, bytes]
_ValueType = Union[str, bytes]

class error(Exception): ...

def whichdb(filename: str) -> str: ...

def open(file: str, flag: str = ..., mode: int = ...) -> MutableMapping[_KeyType, _ValueType]: ...
