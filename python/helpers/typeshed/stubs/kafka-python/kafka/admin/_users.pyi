from _typeshed import ReadableBuffer
from collections.abc import Sequence
from enum import IntEnum
from typing import Final

class UserAdminMixin:
    def alter_user_scram_credentials(self, alterations: Sequence[UserScramCredentialDeletion | UserScramCredentialUpsertion]): ...
    def describe_user_scram_credentials(self, users: Sequence[str] | None = None): ...

class ScramMechanism(IntEnum):
    UNKNOWN = 0
    SCRAM_SHA_256 = 1
    SCRAM_SHA_512 = 2
    @property
    def hash_name(self) -> str: ...

class UserScramCredentialDeletion:
    user: str
    mechanism: ScramMechanism
    def __init__(self, user: str, mechanism: ScramMechanism | int | str) -> None: ...

class UserScramCredentialUpsertion:
    DEFAULT_ITERATIONS: Final = 4096
    user: str
    mechanism: ScramMechanism
    iterations: int
    salt: ReadableBuffer
    salted_password: bytes
    def __init__(
        self,
        user: str,
        mechanism: ScramMechanism | int | str,
        password: str | ReadableBuffer,
        iterations: int | None = None,
        salt: ReadableBuffer | None = None,
    ) -> None: ...
