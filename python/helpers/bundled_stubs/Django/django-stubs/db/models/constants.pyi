from enum import Enum
from typing import cast

LOOKUP_SEP: str

class OnConflict(Enum):
    IGNORE = cast(str, ...)
    UPDATE = cast(str, ...)
