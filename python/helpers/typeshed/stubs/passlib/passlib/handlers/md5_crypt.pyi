from typing import Any

import passlib.utils.handlers as uh

class _MD5_Common(uh.HasSalt, uh.GenericHandler):  # type: ignore
    setting_kwds: Any
    checksum_size: int
    checksum_chars: Any
    max_salt_size: int
    salt_chars: Any
    @classmethod
    def from_string(cls, hash): ...
    def to_string(self): ...

class md5_crypt(uh.HasManyBackends, _MD5_Common):  # type: ignore
    name: str
    ident: Any
    backends: Any

class apr_md5_crypt(_MD5_Common):
    name: str
    ident: Any
