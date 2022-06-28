from typing import Any, ClassVar

import passlib.utils.handlers as uh

log: Any

class sha1_crypt(uh.HasManyBackends, uh.HasRounds, uh.HasSalt, uh.GenericHandler):  # type: ignore
    name: str
    setting_kwds: Any
    ident: Any
    checksum_size: int
    checksum_chars: Any
    default_salt_size: ClassVar[int]
    max_salt_size: int
    salt_chars: Any
    default_rounds: int
    min_rounds: int
    max_rounds: int
    rounds_cost: str
    @classmethod
    def from_string(cls, hash): ...
    def to_string(self, config: bool = ...): ...
    backends: Any
