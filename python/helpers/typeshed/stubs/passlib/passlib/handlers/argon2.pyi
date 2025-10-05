from typing import Any, ClassVar
from typing_extensions import Self

import passlib.utils.handlers as uh

class _DummyCffiHasher:
    time_cost: int
    memory_cost: int
    parallelism: int
    salt_len: int
    hash_len: int

class _Argon2Common(  # type: ignore[misc]
    uh.SubclassBackendMixin, uh.ParallelismMixin, uh.HasRounds, uh.HasRawSalt, uh.HasRawChecksum, uh.GenericHandler
):
    name: ClassVar[str]
    checksum_size: ClassVar[int]
    default_salt_size: ClassVar[int]
    min_salt_size: ClassVar[int]
    max_salt_size: ClassVar[int]
    default_rounds: ClassVar[int]
    min_rounds: ClassVar[int]
    max_rounds: ClassVar[int]
    rounds_cost: ClassVar[str]
    max_parallelism: ClassVar[int]
    max_version: ClassVar[int]
    min_desired_version: ClassVar[int | None]
    min_memory_cost: ClassVar[int]
    max_threads: ClassVar[int]
    pure_use_threads: ClassVar[bool]
    def type_values(cls): ...
    type: str
    parallelism: int
    version: int
    memory_cost: int
    @property
    def type_d(self): ...
    data: Any
    @classmethod
    def using(  # type: ignore[override]
        cls,
        type=None,
        memory_cost=None,
        salt_len=None,
        time_cost=None,
        digest_size=None,
        checksum_size=None,
        hash_len=None,
        max_threads=None,
        **kwds,
    ): ...
    @classmethod
    def identify(cls, hash): ...
    @classmethod
    def from_string(cls, hash) -> Self: ...  # type: ignore[override]
    def __init__(self, type=None, type_d: bool = False, version=None, memory_cost=None, data=None, **kwds) -> None: ...

class _NoBackend(_Argon2Common):
    @classmethod
    def hash(cls, secret): ...  # type: ignore[override]
    @classmethod
    def verify(cls, secret, hash): ...  # type: ignore[override]
    @classmethod
    def genhash(cls, secret, config): ...  # type: ignore[override]

class _CffiBackend(_Argon2Common):
    @classmethod
    def hash(cls, secret): ...  # type: ignore[override]
    @classmethod
    def verify(cls, secret, hash): ...  # type: ignore[override]
    @classmethod
    def genhash(cls, secret, config): ...  # type: ignore[override]

class _PureBackend(_Argon2Common): ...

class argon2(_NoBackend, _Argon2Common):  # type: ignore[misc]
    backends: ClassVar[tuple[str, ...]]

__all__ = ["argon2"]
