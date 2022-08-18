from typing import Any

import passlib.utils.handlers as uh

class _SHA2_Common(uh.HasManyBackends, uh.HasRounds, uh.HasSalt, uh.GenericHandler):  # type: ignore
    setting_kwds: Any
    checksum_chars: Any
    max_salt_size: int
    salt_chars: Any
    min_rounds: int
    max_rounds: int
    rounds_cost: str
    implicit_rounds: bool
    def __init__(self, implicit_rounds: Any | None = ..., **kwds) -> None: ...
    @classmethod
    def from_string(cls, hash): ...
    def to_string(self): ...
    backends: Any

class sha256_crypt(_SHA2_Common):
    name: str
    ident: Any
    checksum_size: int
    default_rounds: int

class sha512_crypt(_SHA2_Common):
    name: str
    ident: Any
    checksum_size: int
    default_rounds: int
