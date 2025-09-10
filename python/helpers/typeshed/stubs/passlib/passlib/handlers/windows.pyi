from typing import Any, ClassVar, Literal, overload

import passlib.utils.handlers as uh

class lmhash(uh.TruncateMixin, uh.HasEncodingContext, uh.StaticHandler):
    name: ClassVar[str]
    checksum_chars: ClassVar[str]
    checksum_size: ClassVar[int]
    truncate_size: ClassVar[int]
    @classmethod
    def raw(cls, secret, encoding=None): ...

class nthash(uh.StaticHandler):
    name: ClassVar[str]
    checksum_chars: ClassVar[str]
    checksum_size: ClassVar[int]
    @classmethod
    def raw(cls, secret): ...
    @overload
    @classmethod
    def raw_nthash(cls, secret: str | bytes, hex: Literal[True]) -> str: ...
    @overload
    @classmethod
    def raw_nthash(cls, secret: str | bytes, hex: Literal[False] = False) -> bytes: ...
    @overload
    @classmethod
    def raw_nthash(cls, secret: str | bytes, hex: bool = False) -> str | bytes: ...

bsd_nthash: Any

class msdcc(uh.HasUserContext, uh.StaticHandler):
    name: ClassVar[str]
    checksum_chars: ClassVar[str]
    checksum_size: ClassVar[int]
    @classmethod
    def raw(cls, secret, user): ...

class msdcc2(uh.HasUserContext, uh.StaticHandler):
    name: ClassVar[str]
    checksum_chars: ClassVar[str]
    checksum_size: ClassVar[int]
    @classmethod
    def raw(cls, secret, user): ...

__all__ = ["lmhash", "nthash", "bsd_nthash", "msdcc", "msdcc2"]
