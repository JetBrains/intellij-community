from typing import Any

import passlib.utils.handlers as uh

class oracle10(uh.HasUserContext, uh.StaticHandler):  # type: ignore
    name: str
    checksum_chars: Any
    checksum_size: int

class oracle11(uh.HasSalt, uh.GenericHandler):  # type: ignore
    name: str
    setting_kwds: Any
    checksum_size: int
    checksum_chars: Any
    min_salt_size: int
    max_salt_size: int
    salt_chars: Any
    @classmethod
    def from_string(cls, hash): ...
    def to_string(self): ...

# Names in __all__ with no definition:
#   oracle10g
#   oracle11g
