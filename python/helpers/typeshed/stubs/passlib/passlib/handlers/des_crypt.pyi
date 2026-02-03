from typing import ClassVar
from typing_extensions import Self

import passlib.utils.handlers as uh

class des_crypt(uh.TruncateMixin, uh.HasManyBackends, uh.HasSalt, uh.GenericHandler):  # type: ignore[misc]
    name: ClassVar[str]
    checksum_chars: ClassVar[str]
    checksum_size: ClassVar[int]
    min_salt_size: ClassVar[int]
    max_salt_size: ClassVar[int]
    salt_chars: ClassVar[str]
    truncate_size: ClassVar[int]
    @classmethod
    def from_string(cls, hash) -> Self: ...  # type: ignore[override]
    backends: ClassVar[tuple[str, ...]]

class bsdi_crypt(uh.HasManyBackends, uh.HasRounds, uh.HasSalt, uh.GenericHandler):  # type: ignore[misc]
    name: ClassVar[str]
    checksum_size: ClassVar[int]
    checksum_chars: ClassVar[str]
    min_salt_size: ClassVar[int]
    max_salt_size: ClassVar[int]
    salt_chars: ClassVar[str]
    default_rounds: ClassVar[int]
    min_rounds: ClassVar[int]
    max_rounds: ClassVar[int]
    rounds_cost: ClassVar[str]
    @classmethod
    def from_string(cls, hash) -> Self: ...  # type: ignore[override]
    @classmethod
    def using(cls, **kwds): ...  # type: ignore[override]
    backends: ClassVar[tuple[str, ...]]

class bigcrypt(uh.HasSalt, uh.GenericHandler):
    name: ClassVar[str]
    checksum_chars: ClassVar[str]
    min_salt_size: ClassVar[int]
    max_salt_size: ClassVar[int]
    salt_chars: ClassVar[str]
    @classmethod
    def from_string(cls, hash) -> Self: ...  # type: ignore[override]

class crypt16(uh.TruncateMixin, uh.HasSalt, uh.GenericHandler):  # type: ignore[misc]
    name: ClassVar[str]
    checksum_size: ClassVar[int]
    checksum_chars: ClassVar[str]
    min_salt_size: ClassVar[int]
    max_salt_size: ClassVar[int]
    salt_chars: ClassVar[str]
    truncate_size: ClassVar[int]
    @classmethod
    def from_string(cls, hash) -> Self: ...  # type: ignore[override]

__all__ = ["des_crypt", "bsdi_crypt", "bigcrypt", "crypt16"]
