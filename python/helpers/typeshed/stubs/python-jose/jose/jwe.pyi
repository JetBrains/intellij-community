from typing import Any

def encrypt(
    plaintext: Any,
    key: dict[str, str],
    encryption=...,
    algorithm=...,
    zip: Any | None = ...,
    cty: Any | None = ...,
    kid: Any | None = ...,
): ...
def decrypt(jwe_str: str, key: str | dict[str, str]): ...
def get_unverified_header(jwe_str: str): ...
