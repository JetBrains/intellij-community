import abc
from typing import Any, ClassVar

from passlib.ifc import PasswordHash
from passlib.utils.binary import BASE64_CHARS, HASH64_CHARS, LOWER_HEX_CHARS, PADDED_BASE64_CHARS, UPPER_HEX_CHARS

H64_CHARS = HASH64_CHARS
B64_CHARS = BASE64_CHARS
PADDED_B64_CHARS = PADDED_BASE64_CHARS
UC_HEX_CHARS = UPPER_HEX_CHARS
LC_HEX_CHARS = LOWER_HEX_CHARS

def parse_mc2(hash, prefix, sep=..., handler: Any | None = ...): ...
def parse_mc3(hash, prefix, sep=..., rounds_base: int = ..., default_rounds: Any | None = ..., handler: Any | None = ...): ...
def render_mc2(ident, salt, checksum, sep=...): ...
def render_mc3(ident, rounds, salt, checksum, sep=..., rounds_base: int = ...): ...

class MinimalHandler(PasswordHash, metaclass=abc.ABCMeta):
    @classmethod
    def using(cls, relaxed: bool = ...): ...  # type: ignore[override]

class TruncateMixin(MinimalHandler, metaclass=abc.ABCMeta):
    truncate_error: bool
    truncate_verify_reject: bool
    @classmethod
    def using(cls, truncate_error: Any | None = ..., **kwds): ...  # type: ignore[override]

class GenericHandler(MinimalHandler):
    setting_kwds: Any
    context_kwds: Any
    ident: Any
    checksum_size: Any
    checksum_chars: Any
    checksum: Any
    use_defaults: Any
    def __init__(self, checksum: Any | None = ..., use_defaults: bool = ..., **kwds) -> None: ...
    @classmethod
    def identify(cls, hash): ...
    @classmethod
    def from_string(cls, hash, **context) -> None: ...
    def to_string(self) -> None: ...
    @classmethod
    def hash(cls, secret, **kwds): ...
    @classmethod
    def verify(cls, secret, hash, **context): ...
    @classmethod
    def genconfig(cls, **kwds): ...
    @classmethod
    def genhash(cls, secret, config, **context): ...
    @classmethod
    def needs_update(cls, hash, secret: Any | None = ..., **kwds): ...
    @classmethod
    def parsehash(cls, hash, checksum: bool = ..., sanitize: bool = ...): ...
    @classmethod
    def bitsize(cls, **kwds): ...

class StaticHandler(GenericHandler):
    setting_kwds: Any
    @classmethod
    def from_string(cls, hash, **context): ...
    def to_string(self): ...

class HasEncodingContext(GenericHandler):
    context_kwds: Any
    default_encoding: str
    encoding: Any
    def __init__(self, encoding: Any | None = ..., **kwds) -> None: ...

class HasUserContext(GenericHandler):
    context_kwds: Any
    user: Any
    def __init__(self, user: Any | None = ..., **kwds) -> None: ...
    @classmethod
    def hash(cls, secret, user: Any | None = ..., **context): ...
    @classmethod
    def verify(cls, secret, hash, user: Any | None = ..., **context): ...
    @classmethod
    def genhash(cls, secret, config, user: Any | None = ..., **context): ...

class HasRawChecksum(GenericHandler): ...

class HasManyIdents(GenericHandler):
    default_ident: Any
    ident_values: Any
    ident_aliases: Any
    ident: Any
    @classmethod
    def using(cls, default_ident: Any | None = ..., ident: Any | None = ..., **kwds): ...  # type: ignore[override]
    def __init__(self, ident: Any | None = ..., **kwds) -> None: ...
    @classmethod
    def identify(cls, hash): ...

class HasSalt(GenericHandler):
    min_salt_size: int
    max_salt_size: Any
    salt_chars: Any
    default_salt_size: ClassVar[int | None]
    default_salt_chars: ClassVar[int | None]
    salt: Any
    @classmethod
    def using(cls, default_salt_size: Any | None = ..., salt_size: Any | None = ..., salt: Any | None = ..., **kwds): ...  # type: ignore[override]
    def __init__(self, salt: Any | None = ..., **kwds) -> None: ...
    @classmethod
    def bitsize(cls, salt_size: Any | None = ..., **kwds): ...

class HasRawSalt(HasSalt):
    salt_chars: Any

class HasRounds(GenericHandler):
    min_rounds: int
    max_rounds: Any
    rounds_cost: str
    using_rounds_kwds: Any
    min_desired_rounds: Any
    max_desired_rounds: Any
    default_rounds: Any
    vary_rounds: Any
    rounds: Any
    @classmethod
    def using(  # type: ignore[override]
        cls,
        min_desired_rounds: Any | None = ...,
        max_desired_rounds: Any | None = ...,
        default_rounds: Any | None = ...,
        vary_rounds: Any | None = ...,
        min_rounds: Any | None = ...,
        max_rounds: Any | None = ...,
        rounds: Any | None = ...,
        **kwds,
    ): ...
    def __init__(self, rounds: Any | None = ..., **kwds) -> None: ...
    @classmethod
    def bitsize(cls, rounds: Any | None = ..., vary_rounds: float = ..., **kwds): ...

class ParallelismMixin(GenericHandler):
    parallelism: int
    @classmethod
    def using(cls, parallelism: Any | None = ..., **kwds): ...  # type: ignore[override]
    def __init__(self, parallelism: Any | None = ..., **kwds) -> None: ...

class BackendMixin(PasswordHash, metaclass=abc.ABCMeta):
    backends: Any
    @classmethod
    def get_backend(cls): ...
    @classmethod
    def has_backend(cls, name: str = ...): ...
    @classmethod
    def set_backend(cls, name: str = ..., dryrun: bool = ...): ...

class SubclassBackendMixin(BackendMixin, metaclass=abc.ABCMeta): ...
class HasManyBackends(BackendMixin, GenericHandler): ...  # type: ignore

class PrefixWrapper:
    name: Any
    prefix: Any
    orig_prefix: Any
    __doc__: Any
    def __init__(
        self, name, wrapped, prefix=..., orig_prefix=..., lazy: bool = ..., doc: Any | None = ..., ident: Any | None = ...
    ) -> None: ...
    wrapped: Any
    @property
    def ident(self): ...
    @property
    def ident_values(self): ...
    def __dir__(self): ...
    def __getattr__(self, attr): ...
    def __setattr__(self, attr, value): ...
    def using(self, **kwds): ...
    def needs_update(self, hash, **kwds): ...
    def identify(self, hash): ...
    def genconfig(self, **kwds): ...
    def genhash(self, secret, config, **kwds): ...
    def encrypt(self, secret, **kwds): ...
    def hash(self, secret, **kwds): ...
    def verify(self, secret, hash, **kwds): ...
