from enum import auto, Enum


class MyEnumBase(Enum):
    @staticmethod
    def _generate_next_value_(name: str, start: int, count: int, last_values: list[str]) -> str: ...


class MyEnumDerived(MyEnumBase):
    FOO = auto()
