from typing import Any, ClassVar

import passlib.utils.handlers as uh

class scrypt(uh.ParallelismMixin, uh.HasRounds, uh.HasRawSalt, uh.HasRawChecksum, uh.HasManyIdents, uh.GenericHandler):  # type: ignore
    backends: ClassVar[tuple[str, ...]]
    name: str
    setting_kwds: Any
    checksum_size: int
    default_ident: Any
    ident_values: Any
    default_salt_size: ClassVar[int]
    max_salt_size: int
    default_rounds: int
    min_rounds: int
    max_rounds: int
    rounds_cost: str
    parallelism: int
    block_size: int
    @classmethod
    def using(cls, block_size: Any | None = ..., **kwds): ...  # type: ignore[override]
    @classmethod
    def from_string(cls, hash): ...
    @classmethod
    def parse(cls, hash): ...
    def to_string(self): ...
    def __init__(self, block_size: Any | None = ..., **kwds) -> None: ...
    @classmethod
    def get_backend(cls): ...
    @classmethod
    def has_backend(cls, name: str = ...): ...
    @classmethod
    def set_backend(cls, name: str = ..., dryrun: bool = ...) -> None: ...
