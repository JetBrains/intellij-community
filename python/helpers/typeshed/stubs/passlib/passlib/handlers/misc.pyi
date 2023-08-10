from typing import Any, ClassVar

import passlib.utils.handlers as uh
from passlib.ifc import DisabledHash

class unix_fallback(DisabledHash, uh.StaticHandler):
    name: ClassVar[str]
    @classmethod
    def identify(cls, hash: str | bytes) -> bool: ...
    enable_wildcard: Any
    def __init__(self, enable_wildcard: bool = ..., **kwds) -> None: ...
    @classmethod
    def verify(cls, secret: str | bytes, hash: str | bytes, enable_wildcard: bool = ...): ...  # type: ignore[override]

class unix_disabled(DisabledHash, uh.MinimalHandler):
    name: ClassVar[str]
    default_marker: ClassVar[str]
    @classmethod
    def using(cls, marker: Any | None = ..., **kwds): ...  # type: ignore[override]
    @classmethod
    def identify(cls, hash: str | bytes) -> bool: ...
    @classmethod
    def verify(cls, secret: str | bytes, hash: str | bytes) -> bool: ...  # type: ignore[override]
    @classmethod
    def hash(cls, secret: str | bytes, **kwds) -> str: ...
    @classmethod
    def genhash(cls, secret: str | bytes, config, marker: Any | None = ...): ...  # type: ignore[override]
    @classmethod
    def disable(cls, hash: str | bytes | None = ...) -> str: ...
    @classmethod
    def enable(cls, hash: str | bytes) -> str: ...

class plaintext(uh.MinimalHandler):
    name: ClassVar[str]
    default_encoding: ClassVar[str]
    @classmethod
    def identify(cls, hash: str | bytes): ...
    @classmethod
    def hash(cls, secret: str | bytes, encoding: Any | None = ...): ...  # type: ignore[override]
    @classmethod
    def verify(cls, secret: str | bytes, hash: str | bytes, encoding: str | None = ...): ...  # type: ignore[override]
    @classmethod
    def genconfig(cls): ...
    @classmethod
    def genhash(cls, secret, config, encoding: str | None = ...): ...  # type: ignore[override]
