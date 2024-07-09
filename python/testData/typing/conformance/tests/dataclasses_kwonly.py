"""
Tests the keyword-only feature of dataclass added in Python 3.10.
"""

# Specification: https://docs.python.org/3/library/dataclasses.html#module-contents

from dataclasses import dataclass, KW_ONLY, field


@dataclass
class DC1:
    a: str
    _: KW_ONLY
    b: int = 0


DC1("hi")
DC1(a="hi")
DC1(a="hi", b=1)
DC1("hi", b=1)

# This should generate an error because "b" is keyword-only.
DC1("hi", 1)  # E


@dataclass
class DC2:
    b: int = field(kw_only=True, default=3)
    a: str


DC2("hi")
DC2(a="hi")
DC2(a="hi", b=1)
DC2("hi", b=1)

# This should generate an error because "b" is keyword-only.
DC2("hi", 1)  # E


@dataclass(kw_only=True)
class DC3:
    a: str = field(kw_only=False)
    b: int = 0


DC3("hi")
DC3(a="hi")
DC3(a="hi", b=1)
DC3("hi", b=1)

# This should generate an error because "b" is keyword-only.
DC3("hi", 1)  # E


@dataclass
class DC4(DC3):
    c: float


DC4("", 0.2, b=3)
DC4(a="", b=3, c=0.2)
