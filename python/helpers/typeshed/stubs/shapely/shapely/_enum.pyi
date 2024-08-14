from enum import IntEnum

class ParamEnum(IntEnum):
    @classmethod
    def get_value(cls, item: str) -> int: ...
