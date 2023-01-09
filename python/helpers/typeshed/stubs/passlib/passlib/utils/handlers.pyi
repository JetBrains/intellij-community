import abc
from _typeshed import Self
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
    def using(cls: Self, relaxed: bool = ...) -> type[Self]: ...  # type: ignore[override]

class TruncateMixin(MinimalHandler, metaclass=abc.ABCMeta):
    truncate_error: ClassVar[bool]
    truncate_verify_reject: ClassVar[bool]
    @classmethod
    def using(cls: type[Self], truncate_error: object = ..., *, relaxed: bool = ...) -> type[Self]: ...  # type: ignore[override]

class GenericHandler(MinimalHandler):
    setting_kwds: ClassVar[tuple[str, ...]]
    context_kwds: ClassVar[tuple[str, ...]]
    ident: ClassVar[str | None]
    checksum_size: ClassVar[int | None]
    checksum_chars: ClassVar[str | None]
    checksum: str | None
    use_defaults: bool
    def __init__(self, checksum: str | None = ..., use_defaults: bool = ...) -> None: ...
    @classmethod
    def identify(cls, hash: str | bytes) -> bool: ...
    @classmethod
    def from_string(cls: type[Self], hash: str | bytes, **context: Any) -> Self: ...
    def to_string(self) -> str: ...
    @classmethod
    def hash(cls, secret: str | bytes, **kwds: Any) -> str: ...
    @classmethod
    def verify(cls, secret: str | bytes, hash: str | bytes, **context: Any) -> bool: ...
    @classmethod
    def genconfig(cls, **kwds: Any) -> str: ...
    @classmethod
    def genhash(cls, secret: str | bytes, config: str, **context: Any) -> str: ...
    @classmethod
    def needs_update(cls, hash: str | bytes, secret: str | bytes | None = ..., **kwds: Any) -> bool: ...
    @classmethod
    def parsehash(cls, hash: str | bytes, checksum: bool = ..., sanitize: bool = ...) -> dict[str, Any]: ...
    @classmethod
    def bitsize(cls, **kwds: Any) -> dict[str, Any]: ...

class StaticHandler(GenericHandler):
    setting_kwds: ClassVar[tuple[str, ...]]

class HasEncodingContext(GenericHandler):
    default_encoding: ClassVar[str]
    encoding: str
    def __init__(self, encoding: str | None = ..., **kwds) -> None: ...

class HasUserContext(GenericHandler):
    user: Any | None
    def __init__(self, user: Any | None = ..., **kwds) -> None: ...
    @classmethod
    def hash(cls, secret, user: Any | None = ..., **context): ...
    @classmethod
    def verify(cls, secret, hash, user: Any | None = ..., **context): ...
    @classmethod
    def genhash(cls, secret, config, user: Any | None = ..., **context): ...

class HasRawChecksum(GenericHandler): ...

class HasManyIdents(GenericHandler):
    default_ident: ClassVar[str | None]
    ident_values: ClassVar[tuple[str, ...] | None]
    ident_aliases: ClassVar[dict[str, str] | None]
    ident: str  # type: ignore[misc]
    @classmethod
    def using(cls, default_ident: Any | None = ..., ident: Any | None = ..., **kwds): ...  # type: ignore[override]
    def __init__(self, ident: Any | None = ..., **kwds) -> None: ...

class HasSalt(GenericHandler):
    min_salt_size: ClassVar[int]
    max_salt_size: ClassVar[int | None]
    salt_chars: ClassVar[str | None]
    default_salt_size: ClassVar[int | None]
    default_salt_chars: ClassVar[str | None]
    salt: str | bytes | None
    @classmethod
    def using(cls, default_salt_size: int | None = ..., salt_size: int | None = ..., salt: str | bytes | None = ..., **kwds): ...  # type: ignore[override]
    def __init__(self, salt: str | bytes | None = ..., **kwds) -> None: ...
    @classmethod
    def bitsize(cls, salt_size: int | None = ..., **kwds): ...

class HasRawSalt(HasSalt):
    salt_chars: ClassVar[bytes]  # type: ignore[assignment]

class HasRounds(GenericHandler):
    min_rounds: ClassVar[int]
    max_rounds: ClassVar[int | None]
    rounds_cost: ClassVar[str]
    using_rounds_kwds: ClassVar[tuple[str, ...]]
    min_desired_rounds: ClassVar[int | None]
    max_desired_rounds: ClassVar[int | None]
    default_rounds: ClassVar[int | None]
    vary_rounds: ClassVar[Any | None]
    rounds: int
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
    backends: ClassVar[tuple[str, ...] | None]
    @classmethod
    def get_backend(cls): ...
    @classmethod
    def has_backend(cls, name: str = ...) -> bool: ...
    @classmethod
    def set_backend(cls, name: str = ..., dryrun: bool = ...): ...

class SubclassBackendMixin(BackendMixin, metaclass=abc.ABCMeta): ...
class HasManyBackends(BackendMixin, GenericHandler): ...

class PrefixWrapper:
    name: Any
    prefix: Any
    orig_prefix: Any
    __doc__: Any
    def __init__(
        self, name, wrapped, prefix=..., orig_prefix=..., lazy: bool = ..., doc: Any | None = ..., ident: Any | None = ...
    ) -> None: ...
    @property
    def wrapped(self): ...
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
