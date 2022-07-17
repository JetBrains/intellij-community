from typing import Any, ClassVar

import passlib.utils.handlers as uh

class fshp(uh.HasRounds, uh.HasRawSalt, uh.HasRawChecksum, uh.GenericHandler):  # type: ignore
    name: str
    setting_kwds: Any
    checksum_chars: Any
    ident: Any
    default_salt_size: ClassVar[int]
    max_salt_size: Any
    default_rounds: int
    min_rounds: int
    max_rounds: int
    rounds_cost: str
    default_variant: int
    @classmethod
    def using(cls, variant: Any | None = ..., **kwds): ...  # type: ignore[override]
    variant: Any
    use_defaults: Any
    def __init__(self, variant: Any | None = ..., **kwds) -> None: ...
    @property
    def checksum_alg(self): ...
    @property
    def checksum_size(self): ...
    @classmethod
    def from_string(cls, hash): ...
    def to_string(self): ...
