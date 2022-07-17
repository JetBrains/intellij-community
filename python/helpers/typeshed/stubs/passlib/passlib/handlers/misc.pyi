from typing import Any

import passlib.utils.handlers as uh
from passlib.ifc import DisabledHash

class unix_fallback(DisabledHash, uh.StaticHandler):  # type: ignore
    name: str
    context_kwds: Any
    @classmethod
    def identify(cls, hash): ...
    enable_wildcard: Any
    def __init__(self, enable_wildcard: bool = ..., **kwds) -> None: ...
    @classmethod
    def verify(cls, secret, hash, enable_wildcard: bool = ...): ...  # type: ignore[override]

class unix_disabled(DisabledHash, uh.MinimalHandler):  # type: ignore
    name: str
    setting_kwds: Any
    context_kwds: Any
    default_marker: Any
    @classmethod
    def using(cls, marker: Any | None = ..., **kwds): ...  # type: ignore[override]
    @classmethod
    def identify(cls, hash): ...
    @classmethod
    def verify(cls, secret, hash): ...
    @classmethod
    def hash(cls, secret, **kwds): ...
    @classmethod
    def genhash(cls, secret, config, marker: Any | None = ...): ...  # type: ignore[override]
    @classmethod
    def disable(cls, hash: Any | None = ...): ...
    @classmethod
    def enable(cls, hash): ...

class plaintext(uh.MinimalHandler):
    name: str
    setting_kwds: Any
    context_kwds: Any
    default_encoding: str
    @classmethod
    def identify(cls, hash): ...
    @classmethod
    def hash(cls, secret, encoding: Any | None = ...): ...  # type: ignore[override]
    @classmethod
    def verify(cls, secret, hash, encoding: Any | None = ...): ...  # type: ignore[override]
    @classmethod
    def genconfig(cls): ...
    @classmethod
    def genhash(cls, secret, config, encoding: Any | None = ...): ...  # type: ignore[override]
