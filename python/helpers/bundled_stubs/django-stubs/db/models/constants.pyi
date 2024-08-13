from enum import Enum

LOOKUP_SEP: str

class OnConflict(Enum):
    IGNORE: str
    UPDATE: str
