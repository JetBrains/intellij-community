from typing import Any, ClassVar

import passlib.utils.handlers as uh

class scram(uh.HasRounds, uh.HasRawSalt, uh.HasRawChecksum, uh.GenericHandler):  # type: ignore
    name: str
    setting_kwds: Any
    ident: Any
    default_salt_size: ClassVar[int]
    max_salt_size: int
    default_rounds: int
    min_rounds: int
    max_rounds: Any
    rounds_cost: str
    default_algs: Any
    algs: Any
    @classmethod
    def extract_digest_info(cls, hash, alg): ...
    @classmethod
    def extract_digest_algs(cls, hash, format: str = ...): ...
    @classmethod
    def derive_digest(cls, password, salt, rounds, alg): ...
    @classmethod
    def from_string(cls, hash): ...
    def to_string(self): ...
    @classmethod
    def using(cls, default_algs: Any | None = ..., algs: Any | None = ..., **kwds): ...  # type: ignore[override]
    def __init__(self, algs: Any | None = ..., **kwds) -> None: ...
    @classmethod
    def verify(cls, secret, hash, full: bool = ...): ...  # type: ignore[override]
