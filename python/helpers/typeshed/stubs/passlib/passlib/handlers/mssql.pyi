from typing import Any

import passlib.utils.handlers as uh

class mssql2000(uh.HasRawSalt, uh.HasRawChecksum, uh.GenericHandler):  # type: ignore
    name: str
    setting_kwds: Any
    checksum_size: int
    min_salt_size: int
    max_salt_size: int
    @classmethod
    def identify(cls, hash): ...
    @classmethod
    def from_string(cls, hash): ...
    def to_string(self): ...
    @classmethod
    def verify(cls, secret, hash): ...

class mssql2005(uh.HasRawSalt, uh.HasRawChecksum, uh.GenericHandler):  # type: ignore
    name: str
    setting_kwds: Any
    checksum_size: int
    min_salt_size: int
    max_salt_size: int
    @classmethod
    def identify(cls, hash): ...
    @classmethod
    def from_string(cls, hash): ...
    def to_string(self): ...
