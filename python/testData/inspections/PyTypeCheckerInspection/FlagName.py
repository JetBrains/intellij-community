from enum import IntEnum, IntFlag, StrEnum


def test_int_flag(x: IntFlag) -> str | None:
    return x.name


