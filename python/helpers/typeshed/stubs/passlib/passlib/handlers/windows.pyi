from typing import Any

import passlib.utils.handlers as uh

class lmhash(uh.TruncateMixin, uh.HasEncodingContext, uh.StaticHandler):  # type: ignore
    name: str
    setting_kwds: Any
    checksum_chars: Any
    checksum_size: int
    truncate_size: int
    default_encoding: str
    @classmethod
    def raw(cls, secret, encoding: Any | None = ...): ...

class nthash(uh.StaticHandler):
    name: str
    checksum_chars: Any
    checksum_size: int
    @classmethod
    def raw(cls, secret): ...
    @classmethod
    def raw_nthash(cls, secret, hex: bool = ...): ...

bsd_nthash: Any

class msdcc(uh.HasUserContext, uh.StaticHandler):  # type: ignore
    name: str
    checksum_chars: Any
    checksum_size: int
    @classmethod
    def raw(cls, secret, user): ...

class msdcc2(uh.HasUserContext, uh.StaticHandler):  # type: ignore
    name: str
    checksum_chars: Any
    checksum_size: int
    @classmethod
    def raw(cls, secret, user): ...
