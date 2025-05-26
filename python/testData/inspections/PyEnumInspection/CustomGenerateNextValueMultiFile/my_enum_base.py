from enum import Enum


class MyVal: ...


class MyEnumBase(Enum):
    _value_: MyVal

    @staticmethod
    def _generate_next_value_(name: str, start: int, count: int, last_values: list[MyVal]) -> MyVal: ...
