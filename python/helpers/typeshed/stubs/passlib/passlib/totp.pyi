from typing import Any

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
    default_tag: Any
    def __init__(
        self,
        secrets: Any | None = ...,
        default_tag: Any | None = ...,
        encrypt_cost: Any | None = ...,
        secrets_path: Any | None = ...,
    ) -> None: ...
    @property
    def has_secrets(self): ...
    def get_secret(self, tag): ...
    def encrypt_key(self, key): ...
    def decrypt_key(self, enckey): ...

class TOTP:
    min_json_version: int
    json_version: int
    wallet: Any
    now: Any
    digits: int
    alg: str
    label: Any
    issuer: Any
    period: int
    changed: bool
    @classmethod
    def using(
        cls,
        digits: Any | None = ...,
        alg: Any | None = ...,
        period: Any | None = ...,
        issuer: Any | None = ...,
        wallet: Any | None = ...,
        now: Any | None = ...,
        **kwds,
    ): ...
    @classmethod
    def new(cls, **kwds): ...
    def __init__(
        self,
        key: Any | None = ...,
        format: str = ...,
        new: bool = ...,
        digits: Any | None = ...,
        alg: Any | None = ...,
        size: Any | None = ...,
        period: Any | None = ...,
        label: Any | None = ...,
        issuer: Any | None = ...,
        changed: bool = ...,
        **kwds,
    ) -> None: ...
    @property
    def key(self): ...
    @key.setter
    def key(self, value) -> None: ...
    @property
    def encrypted_key(self): ...
    @encrypted_key.setter
    def encrypted_key(self, value) -> None: ...
    @property
    def hex_key(self): ...
    @property
    def base32_key(self): ...
    def pretty_key(self, format: str = ..., sep: str = ...): ...
    @classmethod
    def normalize_time(cls, time): ...
    def normalize_token(self_or_cls, token): ...
    def generate(self, time: Any | None = ...): ...
    @classmethod
    def verify(cls, token, source, **kwds): ...
    def match(self, token, time: Any | None = ..., window: int = ..., skew: int = ..., last_counter: Any | None = ...): ...
    @classmethod
    def from_source(cls, source): ...
    @classmethod
    def from_uri(cls, uri): ...
    def to_uri(self, label: Any | None = ..., issuer: Any | None = ...): ...
    @classmethod
    def from_json(cls, source): ...
    def to_json(self, encrypt: Any | None = ...): ...
    @classmethod
    def from_dict(cls, source): ...
    def to_dict(self, encrypt: Any | None = ...): ...

class TotpToken(SequenceMixin):
    totp: Any
    token: Any
    counter: Any
    def __init__(self, totp, token, counter) -> None: ...
    @property
    def start_time(self): ...
    @property
    def expire_time(self): ...
    @property
    def remaining(self): ...
    @property
    def valid(self): ...

class TotpMatch(SequenceMixin):
    totp: Any
    counter: int
    time: int
    window: int
    def __init__(self, totp, counter, time, window: int = ...) -> None: ...
    @property
    def expected_counter(self): ...
    @property
    def skipped(self): ...
    @property
    def expire_time(self): ...
    @property
    def cache_seconds(self): ...
    @property
    def cache_time(self): ...
