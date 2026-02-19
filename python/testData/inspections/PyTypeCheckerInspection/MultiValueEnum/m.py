import enum


class SuperEnum(enum.Enum):
    PINK = "PINK", "hot"
    FLOSS = "FLOSS", "sweet"


class SimpleEnum(enum.Enum):
    FOO = "FOO"