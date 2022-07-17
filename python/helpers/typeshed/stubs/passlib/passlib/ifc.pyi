import abc
from abc import abstractmethod
from typing import Any

class PasswordHash(metaclass=abc.ABCMeta):
    is_disabled: bool
    truncate_size: Any
    truncate_error: bool
    truncate_verify_reject: bool
    @classmethod
    @abstractmethod
    def hash(cls, secret, **setting_and_context_kwds): ...
    @classmethod
    def encrypt(cls, *args, **kwds): ...
    @classmethod
    @abstractmethod
    def verify(cls, secret, hash, **context_kwds): ...
    @classmethod
    @abstractmethod
    def using(cls, relaxed: bool = ..., **kwds): ...
    @classmethod
    def needs_update(cls, hash, secret: Any | None = ...): ...
    @classmethod
    @abstractmethod
    def identify(cls, hash): ...
    @classmethod
    def genconfig(cls, **setting_kwds): ...
    @classmethod
    def genhash(cls, secret, config, **context) -> None: ...
    deprecated: bool

class DisabledHash(PasswordHash, metaclass=abc.ABCMeta):
    is_disabled: bool
    @classmethod
    def disable(cls, hash: Any | None = ...): ...
    @classmethod
    def enable(cls, hash) -> None: ...
