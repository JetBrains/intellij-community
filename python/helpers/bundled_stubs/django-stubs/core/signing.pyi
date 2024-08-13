from datetime import timedelta
from typing import Any, Protocol, overload, type_check_only

from typing_extensions import deprecated

BASE62_ALPHABET: str

class BadSignature(Exception): ...
class SignatureExpired(BadSignature): ...

def b62_encode(s: int) -> str: ...
def b62_decode(s: str) -> int: ...
def b64_encode(s: bytes) -> bytes: ...
def b64_decode(s: bytes) -> bytes: ...
def base64_hmac(salt: bytes | str, value: bytes | str, key: bytes | str, algorithm: str = ...) -> str: ...
def get_cookie_signer(salt: str = ...) -> TimestampSigner: ...
@type_check_only
class Serializer(Protocol):
    def dumps(self, obj: Any) -> bytes: ...
    def loads(self, data: bytes) -> Any: ...

class JSONSerializer:
    def dumps(self, obj: Any) -> bytes: ...
    def loads(self, data: bytes) -> Any: ...

def dumps(
    obj: Any,
    key: bytes | str | None = ...,
    salt: bytes | str = ...,
    serializer: type[Serializer] = ...,
    compress: bool = ...,
) -> str: ...
def loads(
    s: str,
    key: bytes | str | None = ...,
    salt: bytes | str = ...,
    serializer: type[Serializer] = ...,
    max_age: int | timedelta | None = ...,
    fallback_keys: list[str | bytes] | None = ...,
) -> Any: ...

class Signer:
    key: bytes | str
    fallback_keys: list[bytes | str]
    sep: str
    salt: bytes | str
    algorithm: str
    @overload
    def __init__(
        self,
        *,
        key: bytes | str | None = ...,
        sep: str = ...,
        salt: bytes | str | None = ...,
        algorithm: str | None = ...,
        fallback_keys: list[bytes | str] | None = ...,
    ) -> None: ...
    @overload
    @deprecated("Passing positional arguments to Signer is deprecated and will be removed in Django 5.1")
    def __init__(
        self,
        *args: Any,
        key: bytes | str | None = ...,
        sep: str = ...,
        salt: bytes | str | None = ...,
        algorithm: str | None = ...,
        fallback_keys: list[bytes | str] | None = ...,
    ) -> None: ...
    def signature(self, value: bytes | str, key: bytes | str | None = ...) -> str: ...
    def sign(self, value: str) -> str: ...
    def unsign(self, signed_value: str) -> str: ...
    def sign_object(
        self,
        obj: Any,
        serializer: type[Serializer] = ...,
        compress: bool = ...,
    ) -> str: ...
    def unsign_object(
        self,
        signed_obj: str,
        serializer: type[Serializer] = ...,
        **kwargs: Any,
    ) -> Any: ...

class TimestampSigner(Signer):
    def timestamp(self) -> str: ...
    def sign(self, value: str) -> str: ...
    def unsign(self, value: str, max_age: int | timedelta | None = ...) -> str: ...
