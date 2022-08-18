from typing import Any, ClassVar

import passlib.utils.handlers as uh
from passlib.handlers.bcrypt import _wrapped_bcrypt
from passlib.ifc import DisabledHash

class DjangoSaltedHash(uh.HasSalt, uh.GenericHandler):  # type: ignore
    setting_kwds: Any
    default_salt_size: ClassVar[int]
    max_salt_size: Any
    salt_chars: Any
    checksum_chars: Any
    @classmethod
    def from_string(cls, hash): ...
    def to_string(self): ...

class DjangoVariableHash(uh.HasRounds, DjangoSaltedHash):  # type: ignore
    setting_kwds: Any
    min_rounds: int
    @classmethod
    def from_string(cls, hash): ...
    def to_string(self): ...

class django_salted_sha1(DjangoSaltedHash):
    name: str
    django_name: str
    ident: Any
    checksum_size: int

class django_salted_md5(DjangoSaltedHash):
    name: str
    django_name: str
    ident: Any
    checksum_size: int

django_bcrypt: Any

class django_bcrypt_sha256(_wrapped_bcrypt):
    name: str
    django_name: str
    django_prefix: Any
    @classmethod
    def identify(cls, hash): ...
    @classmethod
    def from_string(cls, hash): ...
    def to_string(self): ...

class django_pbkdf2_sha256(DjangoVariableHash):
    name: str
    django_name: str
    ident: Any
    min_salt_size: int
    max_rounds: int
    checksum_chars: Any
    checksum_size: int
    default_rounds: Any

class django_pbkdf2_sha1(django_pbkdf2_sha256):
    name: str
    django_name: str
    ident: Any
    checksum_size: int
    default_rounds: Any

django_argon2: Any

class django_des_crypt(uh.TruncateMixin, uh.HasSalt, uh.GenericHandler):  # type: ignore
    name: str
    django_name: str
    setting_kwds: Any
    ident: Any
    checksum_chars: Any
    salt_chars: Any
    checksum_size: int
    min_salt_size: int
    default_salt_size: ClassVar[int]
    truncate_size: int
    use_duplicate_salt: bool
    @classmethod
    def from_string(cls, hash): ...
    def to_string(self): ...

class django_disabled(DisabledHash, uh.StaticHandler):  # type: ignore
    name: str
    suffix_length: int
    @classmethod
    def identify(cls, hash): ...
    @classmethod
    def verify(cls, secret, hash): ...
