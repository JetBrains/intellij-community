# Stubs for hashlib

from abc import abstractmethod, ABCMeta
from typing import AbstractSet, Union

_DataType = Union[bytes, bytearray, memoryview]

class Hash(metaclass=ABCMeta):
    digest_size = ...  # type: int
    block_size = ...  # type: int

    # [Python documentation note] Changed in version 3.4: The name attribute has
    # been present in CPython since its inception, but until Python 3.4 was not
    # formally specified, so may not exist on some platforms
    name = ...  # type: str

    @abstractmethod
    def update(self, arg: _DataType) -> None: ...
    @abstractmethod
    def digest(self) -> bytes: ...
    @abstractmethod
    def hexdigest(self) -> str: ...
    @abstractmethod
    def copy(self) -> 'Hash': ...

def md5(arg: _DataType = ...) -> Hash: ...
def sha1(arg: _DataType = ...) -> Hash: ...
def sha224(arg: _DataType = ...) -> Hash: ...
def sha256(arg: _DataType = ...) -> Hash: ...
def sha384(arg: _DataType = ...) -> Hash: ...
def sha512(arg: _DataType = ...) -> Hash: ...

def new(name: str, data: _DataType = ...) -> Hash: ...

# New in version 3.2
algorithms_guaranteed = ...  # type: AbstractSet[str]
algorithms_available = ...  # type: AbstractSet[str]

# New in version 3.4
def pbkdf2_hmac(name: str, password: _DataType, salt: _DataType, rounds: int, dklen: int = ...) -> bytes: ...
