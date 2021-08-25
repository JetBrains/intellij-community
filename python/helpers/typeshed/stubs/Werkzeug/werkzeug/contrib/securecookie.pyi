from hashlib import sha1 as _default_hash
from hmac import new as hmac
from typing import Any

from werkzeug.contrib.sessions import ModificationTrackingDict

class UnquoteError(Exception): ...

class SecureCookie(ModificationTrackingDict[Any, Any]):
    hash_method: Any
    serialization_method: Any
    quote_base64: Any
    secret_key: Any
    new: Any
    def __init__(self, data: Any | None = ..., secret_key: Any | None = ..., new: bool = ...): ...
    @property
    def should_save(self): ...
    @classmethod
    def quote(cls, value): ...
    @classmethod
    def unquote(cls, value): ...
    def serialize(self, expires: Any | None = ...): ...
    @classmethod
    def unserialize(cls, string, secret_key): ...
    @classmethod
    def load_cookie(cls, request, key: str = ..., secret_key: Any | None = ...): ...
    def save_cookie(
        self,
        response,
        key: str = ...,
        expires: Any | None = ...,
        session_expires: Any | None = ...,
        max_age: Any | None = ...,
        path: str = ...,
        domain: Any | None = ...,
        secure: Any | None = ...,
        httponly: bool = ...,
        force: bool = ...,
    ): ...
