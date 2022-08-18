from typing import Any

import passlib.utils.handlers as uh

class phpass(uh.HasManyIdents, uh.HasRounds, uh.HasSalt, uh.GenericHandler):  # type: ignore
    name: str
    setting_kwds: Any
    checksum_chars: Any
    min_salt_size: int
    max_salt_size: int
    salt_chars: Any
    default_rounds: int
    min_rounds: int
    max_rounds: int
    rounds_cost: str
    default_ident: Any
    ident_values: Any
    ident_aliases: Any
    @classmethod
    def from_string(cls, hash): ...
    def to_string(self): ...
