from _hashlib import HASH
from _typeshed import ReadableBuffer
from collections.abc import Callable
from typing import ClassVar, Final

from paramiko.message import Message
from paramiko.transport import Transport

c_MSG_KEXDH_GEX_REQUEST_OLD: Final[bytes]
c_MSG_KEXDH_GEX_GROUP: Final[bytes]
c_MSG_KEXDH_GEX_INIT: Final[bytes]
c_MSG_KEXDH_GEX_REPLY: Final[bytes]
c_MSG_KEXDH_GEX_REQUEST: Final[bytes]

class KexGexSHA256:
    name: ClassVar[str]
    min_bits: ClassVar[int]
    max_bits: ClassVar[int]
    preferred_bits: ClassVar[int]
    hash_algo: ClassVar[Callable[[ReadableBuffer], HASH]]

    transport: Transport
    p: int | None
    q: int | None
    g: int | None
    x: int | None
    e: int | None
    f: int | None
    old_style: bool

    def __init__(self, transport: Transport) -> None: ...
    def start_kex(self, _test_old_style: bool = False) -> None: ...
    def parse_next(self, ptype: int, m: Message) -> None: ...
