from typing import Any, ClassVar

import passlib.utils.handlers as uh

class sun_md5_crypt(uh.HasRounds, uh.HasSalt, uh.GenericHandler):  # type: ignore
    name: str
    setting_kwds: Any
    checksum_chars: Any
    checksum_size: int
    default_salt_size: ClassVar[int]
    max_salt_size: Any
    salt_chars: Any
    default_rounds: int
    min_rounds: int
    max_rounds: int
    rounds_cost: str
    ident_values: Any
    bare_salt: bool
    def __init__(self, bare_salt: bool = ..., **kwds) -> None: ...
    @classmethod
    def identify(cls, hash): ...
    @classmethod
    def from_string(cls, hash): ...
    def to_string(self, _withchk: bool = ...): ...
