# Stubs for hashlib

from abc import abstractmethod, ABCMeta
from typing import AbstractSet

class Hash(metaclass=ABCMeta):
    digest_size = ...  # type: int
    block_size = ...  # type: int

    # [Python documentation note] Changed in version 3.4: The name attribute has
    # been present in CPython since its inception, but until Python 3.4 was not
    # formally specified, so may not exist on some platforms
    name = ...  # type: str

    @abstractmethod
    def update(self, arg: bytes) -> None: ...
    @abstractmethod
    def digest(self) -> bytes: ...
    @abstractmethod
    def hexdigest(self) -> str: ...
    @abstractmethod
    def copy(self) -> 'Hash': ...

def md5(arg: bytes = ...) -> Hash: ...
def sha1(arg: bytes = ...) -> Hash: ...
def sha224(arg: bytes = ...) -> Hash: ...
def sha256(arg: bytes = ...) -> Hash: ...
def sha384(arg: bytes = ...) -> Hash: ...
def sha512(arg: bytes = ...) -> Hash: ...

def new(name: str, data: bytes = ...) -> Hash: ...

# New in version 3.2
algorithms_guaranteed = ...  # type: AbstractSet[str]
algorithms_available = ...  # type: AbstractSet[str]

# New in version 3.4
# TODO The documentation says "password and salt are interpreted as buffers of
# bytes", should we declare something other than bytes here?
def pbkdf2_hmac(name: str, password: bytes, salt: bytes, rounds: int, dklen: int = ...) -> bytes: ...
