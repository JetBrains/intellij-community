from typing import Any

import passlib.utils.handlers as uh

class _BcryptCommon(uh.SubclassBackendMixin, uh.TruncateMixin, uh.HasManyIdents, uh.HasRounds, uh.HasSalt, uh.GenericHandler):  # type: ignore
    name: str
    setting_kwds: Any
    checksum_size: int
    checksum_chars: Any
    default_ident: Any
    ident_values: Any
    ident_aliases: Any
    min_salt_size: int
    max_salt_size: int
    salt_chars: Any
    final_salt_chars: str
    default_rounds: int
    min_rounds: int
    max_rounds: int
    rounds_cost: str
    truncate_size: int
    @classmethod
    def from_string(cls, hash): ...
    def to_string(self): ...
    @classmethod
    def needs_update(cls, hash, **kwds): ...
    @classmethod
    def normhash(cls, hash): ...

class _NoBackend(_BcryptCommon): ...
class _BcryptBackend(_BcryptCommon): ...
class _BcryptorBackend(_BcryptCommon): ...
class _PyBcryptBackend(_BcryptCommon): ...
class _OsCryptBackend(_BcryptCommon): ...
class _BuiltinBackend(_BcryptCommon): ...

class bcrypt(_NoBackend, _BcryptCommon):  # type: ignore
    backends: Any

class _wrapped_bcrypt(bcrypt):
    setting_kwds: Any
    truncate_size: Any

class bcrypt_sha256(_wrapped_bcrypt):
    name: str
    ident_values: Any
    ident_aliases: Any
    default_ident: Any
    version: int
    @classmethod
    def using(cls, version: Any | None = ..., **kwds): ...  # type: ignore[override]
    prefix: Any
    @classmethod
    def identify(cls, hash): ...
    @classmethod
    def from_string(cls, hash): ...
    def to_string(self): ...
    def __init__(self, version: Any | None = ..., **kwds) -> None: ...
