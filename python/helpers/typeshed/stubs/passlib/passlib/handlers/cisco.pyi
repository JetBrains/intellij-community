from typing import Any

import passlib.utils.handlers as uh

class cisco_pix(uh.HasUserContext, uh.StaticHandler):  # type: ignore
    name: str
    truncate_size: int
    truncate_error: bool
    truncate_verify_reject: bool
    checksum_size: int
    checksum_chars: Any

class cisco_asa(cisco_pix):
    name: str
    truncate_size: int

class cisco_type7(uh.GenericHandler):
    name: str
    setting_kwds: Any
    checksum_chars: Any
    min_salt_value: int
    max_salt_value: int
    @classmethod
    def using(cls, salt: Any | None = ..., **kwds): ...  # type: ignore[override]
    @classmethod
    def from_string(cls, hash): ...
    salt: Any
    def __init__(self, salt: Any | None = ..., **kwds) -> None: ...
    def to_string(self): ...
    @classmethod
    def decode(cls, hash, encoding: str = ...): ...
