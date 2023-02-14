from typing import Any

from .backends.base import Key

def encrypt(
    plaintext: str | bytes,
    # Internally it's passed down to jwk.construct(), which explicitly checks for
    # key as dict instance, instead of a Mapping
    key: str | bytes | dict[str, Any] | Key,
    encryption: str = ...,
    algorithm: str = ...,
    zip: str | None = ...,
    cty: str | None = ...,
    kid: str | None = ...,
) -> bytes: ...
def decrypt(
    jwe_str: str | bytes,
    # Internally it's passed down to jwk.construct(), which explicitly checks for
    # key as dict instance, instead of a Mapping
    key: str | bytes | dict[str, Any] | Key,
) -> bytes | None: ...
def get_unverified_header(jwe_str: str | bytes | None) -> dict[str, Any]: ...
