from typing import Any

import passlib.utils.handlers as uh

class des_crypt(uh.TruncateMixin, uh.HasManyBackends, uh.HasSalt, uh.GenericHandler):  # type: ignore
    name: str
    setting_kwds: Any
    checksum_chars: Any
    checksum_size: int
    min_salt_size: int
    max_salt_size: int
    salt_chars: Any
    truncate_size: int
    @classmethod
    def from_string(cls, hash): ...
    def to_string(self): ...
    backends: Any

class bsdi_crypt(uh.HasManyBackends, uh.HasRounds, uh.HasSalt, uh.GenericHandler):  # type: ignore
    name: str
    setting_kwds: Any
    checksum_size: int
    checksum_chars: Any
    min_salt_size: int
    max_salt_size: int
    salt_chars: Any
    default_rounds: int
    min_rounds: int
    max_rounds: int
    rounds_cost: str
    @classmethod
    def from_string(cls, hash): ...
    def to_string(self): ...
    @classmethod
    def using(cls, **kwds): ...
    backends: Any

class bigcrypt(uh.HasSalt, uh.GenericHandler):  # type: ignore
    name: str
    setting_kwds: Any
    checksum_chars: Any
    min_salt_size: int
    max_salt_size: int
    salt_chars: Any
    @classmethod
    def from_string(cls, hash): ...
    def to_string(self): ...

class crypt16(uh.TruncateMixin, uh.HasSalt, uh.GenericHandler):  # type: ignore
    name: str
    setting_kwds: Any
    checksum_size: int
    checksum_chars: Any
    min_salt_size: int
    max_salt_size: int
    salt_chars: Any
    truncate_size: int
    @classmethod
    def from_string(cls, hash): ...
    def to_string(self): ...
