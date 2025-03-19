from collections.abc import Callable
from datetime import datetime
from typing import Any, Literal
from typing_extensions import Self

from passlib.exc import (
    InvalidTokenError as InvalidTokenError,
    MalformedTokenError as MalformedTokenError,
    TokenError as TokenError,
    UsedTokenError as UsedTokenError,
)
from passlib.utils import SequenceMixin

class AppWallet:
    salt_size: int
    encrypt_cost: int
    default_tag: str | None
    def __init__(
        self,
        secrets: dict[int, str] | dict[int, bytes] | dict[str, str] | dict[str, bytes] | str | None = None,
        default_tag: str | None = None,
        encrypt_cost: int | None = None,
        secrets_path: str | None = None,
    ) -> None: ...
    @property
    def has_secrets(self) -> bool: ...
    def get_secret(self, tag: str) -> bytes: ...
    def encrypt_key(self, key: bytes) -> dict[str, Any]: ...
    def decrypt_key(self, enckey: dict[str, Any]) -> tuple[bytes, bool]: ...

class TOTP:
    min_json_version: int
    json_version: int
    wallet: AppWallet | None
    now: Callable[[], float]
    digits: int
    alg: str
    label: str | None
    issuer: str | None
    period: int
    changed: bool
    @classmethod
    def using(
        cls,
        digits: int | None = None,
        alg: Literal["sha1", "sha256", "sha512"] | None = None,
        period: int | None = None,
        issuer: str | None = None,
        wallet: AppWallet | None = None,
        now: Callable[[], float] | None = None,
        *,
        secrets: dict[int, str] | dict[int, bytes] | dict[str, str] | dict[str, bytes] | str | None = None,
        **kwds: Any,
    ) -> type[TOTP]: ...
    @classmethod
    def new(cls, **kwds: Any) -> Self: ...
    def __init__(
        self,
        key: str | bytes | None = None,
        format: str = "base32",
        new: bool = False,
        digits: int | None = None,
        alg: Literal["sha1", "sha256", "sha512"] | None = None,
        size: int | None = None,
        period: int | None = None,
        label: str | None = None,
        issuer: str | None = None,
        changed: bool = False,
        **kwds: Any,
    ) -> None: ...
    @property
    def key(self) -> bytes: ...
    @key.setter
    def key(self, value: bytes) -> None: ...
    @property
    def encrypted_key(self) -> dict[str, Any]: ...
    @encrypted_key.setter
    def encrypted_key(self, value: dict[str, Any]) -> None: ...
    @property
    def hex_key(self) -> str: ...
    @property
    def base32_key(self) -> str: ...
    def pretty_key(self, format: str = "base32", sep: str | Literal[False] = "-") -> str: ...
    @classmethod
    def normalize_time(cls, time: float | datetime | None) -> int: ...
    def normalize_token(self_or_cls, token: bytes | str | int) -> str: ...
    def generate(self, time: float | datetime | None = None) -> TotpToken: ...
    @classmethod
    def verify(
        cls,
        token: int | str,
        source: TOTP | dict[str, Any] | str | bytes,
        *,
        time: float | datetime | None = ...,
        window: int = ...,
        skew: int = ...,
        last_counter: int | None = ...,
    ) -> TotpMatch: ...
    def match(
        self,
        token: int | str,
        time: float | datetime | None = None,
        window: int = 30,
        skew: int = 0,
        last_counter: int | None = None,
    ) -> TotpMatch: ...
    @classmethod
    def from_source(cls, source: TOTP | dict[str, Any] | str | bytes) -> Self: ...
    @classmethod
    def from_uri(cls, uri: str) -> Self: ...
    def to_uri(self, label: str | None = None, issuer: str | None = None) -> str: ...
    @classmethod
    def from_json(cls, source: str | bytes) -> Self: ...
    def to_json(self, encrypt: bool | None = None) -> str: ...
    @classmethod
    def from_dict(cls, source: dict[str, Any]) -> Self: ...
    def to_dict(self, encrypt: bool | None = None) -> dict[str, Any]: ...

class TotpToken(SequenceMixin):
    totp: TOTP | None
    token: str | None
    counter: int | None
    def __init__(self, totp: TOTP, token: str, counter: int) -> None: ...
    @property
    def start_time(self) -> int: ...
    @property
    def expire_time(self) -> int: ...
    @property
    def remaining(self) -> float: ...
    @property
    def valid(self) -> bool: ...

class TotpMatch(SequenceMixin):
    totp: TOTP | None
    counter: int
    time: int
    window: int
    def __init__(self, totp: TOTP, counter: int, time: int, window: int = 30) -> None: ...
    @property
    def expected_counter(self) -> int: ...
    @property
    def skipped(self) -> int: ...
    @property
    def expire_time(self) -> int: ...
    @property
    def cache_seconds(self) -> int: ...
    @property
    def cache_time(self) -> int: ...
