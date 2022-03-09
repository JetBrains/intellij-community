from typing import Any, ClassVar

import passlib.utils.handlers as uh
from passlib.utils.handlers import PrefixWrapper

class Pbkdf2DigestHandler(uh.HasRounds, uh.HasRawSalt, uh.HasRawChecksum, uh.GenericHandler):  # type: ignore
    setting_kwds: Any
    checksum_chars: Any
    default_salt_size: ClassVar[int]
    max_salt_size: int
    default_rounds: Any
    min_rounds: int
    max_rounds: int
    rounds_cost: str
    @classmethod
    def from_string(cls, hash): ...
    def to_string(self): ...

pbkdf2_sha1: Any
pbkdf2_sha256: Any
pbkdf2_sha512: Any

ldap_pbkdf2_sha1: PrefixWrapper
ldap_pbkdf2_sha256: PrefixWrapper
ldap_pbkdf2_sha512: PrefixWrapper

class cta_pbkdf2_sha1(uh.HasRounds, uh.HasRawSalt, uh.HasRawChecksum, uh.GenericHandler):  # type: ignore
    name: str
    setting_kwds: Any
    ident: Any
    checksum_size: int
    default_salt_size: ClassVar[int]
    max_salt_size: int
    default_rounds: Any
    min_rounds: int
    max_rounds: int
    rounds_cost: str
    @classmethod
    def from_string(cls, hash): ...
    def to_string(self): ...

class dlitz_pbkdf2_sha1(uh.HasRounds, uh.HasSalt, uh.GenericHandler):  # type: ignore
    name: str
    setting_kwds: Any
    ident: Any
    default_salt_size: ClassVar[int]
    max_salt_size: int
    salt_chars: Any
    default_rounds: Any
    min_rounds: int
    max_rounds: int
    rounds_cost: str
    @classmethod
    def from_string(cls, hash): ...
    def to_string(self): ...

class atlassian_pbkdf2_sha1(uh.HasRawSalt, uh.HasRawChecksum, uh.GenericHandler):  # type: ignore
    name: str
    setting_kwds: Any
    ident: Any
    checksum_size: int
    min_salt_size: int
    max_salt_size: int
    @classmethod
    def from_string(cls, hash): ...
    def to_string(self): ...

class grub_pbkdf2_sha512(uh.HasRounds, uh.HasRawSalt, uh.HasRawChecksum, uh.GenericHandler):  # type: ignore
    name: str
    setting_kwds: Any
    ident: Any
    checksum_size: int
    default_salt_size: ClassVar[int]
    max_salt_size: int
    default_rounds: Any
    min_rounds: int
    max_rounds: int
    rounds_cost: str
    @classmethod
    def from_string(cls, hash): ...
    def to_string(self): ...
