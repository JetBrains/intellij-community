from typing import Any

import passlib.utils.handlers as uh

class HexDigestHash(uh.StaticHandler):
    checksum_size: Any
    checksum_chars: Any
    supported: bool

def create_hex_hash(digest, module=..., django_name: Any | None = ..., required: bool = ...): ...

hex_md4: Any
hex_md5: Any
hex_sha1: Any
hex_sha256: Any
hex_sha512: Any

class htdigest(uh.MinimalHandler):
    name: str
    setting_kwds: Any
    context_kwds: Any
    default_encoding: str
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
