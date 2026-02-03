from binascii import hexlify as hexlify
from typing import Final, Literal, overload

from passlib.handlers.windows import nthash as nthash

LM_MAGIC: Final[bytes]
raw_nthash = nthash.raw_nthash

@overload
def raw_lmhash(secret: str | bytes, encoding: str = "ascii", hex: Literal[False] = False) -> bytes: ...
@overload
def raw_lmhash(secret: str | bytes, encoding: str, hex: Literal[True]) -> str: ...
@overload
def raw_lmhash(secret: str | bytes, *, hex: Literal[True]) -> str: ...

__all__ = ["nthash", "raw_lmhash", "raw_nthash"]
