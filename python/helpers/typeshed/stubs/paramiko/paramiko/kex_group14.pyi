from _hashlib import HASH
from _typeshed import ReadableBuffer
from collections.abc import Callable
from typing import ClassVar, Final

from paramiko.message import Message
from paramiko.transport import Transport

c_MSG_KEXDH_INIT: Final[bytes]
c_MSG_KEXDH_REPLY: Final[bytes]
b7fffffffffffffff: Final[bytes]
b0000000000000000: Final[bytes]

class KexGroup14SHA256:
    P: ClassVar[int]
    G: ClassVar[int]

    name: ClassVar[str]
    hash_algo: ClassVar[Callable[[ReadableBuffer], HASH]]

    transport: Transport
    x: int
    e: int
    f: int

    def __init__(self, transport: Transport) -> None: ...
    def start_kex(self) -> None: ...
    def parse_next(self, ptype: int, m: Message) -> None: ...
