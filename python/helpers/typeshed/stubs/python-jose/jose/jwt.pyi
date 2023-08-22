from collections.abc import Container, Iterable, Mapping, MutableMapping
from typing import Any

from .backends.base import Key

def encode(
    claims: MutableMapping[str, Any],
    # Internally it calls jws.sign() that expects a key dict instance instead of Mapping
    key: str | bytes | dict[str, Any] | Key,
    algorithm: str = ...,
    headers: Mapping[str, Any] | None = ...,
    access_token: str | None = ...,
) -> str: ...
def decode(
    token: str | bytes,
    key: str | bytes | Mapping[str, Any] | Key,
    algorithms: str | Container[str] | None = ...,
    options: Mapping[str, Any] | None = ...,
    audience: str | None = ...,
    issuer: str | Iterable[str] | None = ...,
    subject: str | None = ...,
    access_token: str | None = ...,
) -> dict[str, Any]: ...
def get_unverified_header(token: str) -> dict[str, Any]: ...
def get_unverified_headers(token: str) -> dict[str, Any]: ...
def get_unverified_claims(token: str) -> dict[str, Any]: ...
