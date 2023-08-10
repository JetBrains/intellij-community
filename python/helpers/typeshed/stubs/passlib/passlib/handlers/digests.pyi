from typing import Any, ClassVar

import passlib.utils.handlers as uh

class HexDigestHash(uh.StaticHandler):
    checksum_chars: ClassVar[str]
    supported: ClassVar[bool]

def create_hex_hash(digest, module=..., django_name: Any | None = ..., required: bool = ...): ...

hex_md4: Any
hex_md5: Any
hex_sha1: Any
hex_sha256: Any
hex_sha512: Any

class htdigest(uh.MinimalHandler):
    name: ClassVar[str]
    default_encoding: ClassVar[str]
    @classmethod
    def hash(cls, secret, user, realm, encoding: Any | None = ...): ...  # type: ignore[override]
    @classmethod
    def verify(cls, secret, hash, user, realm, encoding: str = ...): ...  # type: ignore[override]
    @classmethod
    def identify(cls, hash): ...
    @classmethod
    def genconfig(cls): ...
    @classmethod
    def genhash(cls, secret, config, user, realm, encoding: Any | None = ...): ...  # type: ignore[override]
