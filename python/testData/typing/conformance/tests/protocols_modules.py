"""
Tests the handling of modules as the implementation of a protocol.
"""

# Specification: https://typing.readthedocs.io/en/latest/spec/protocol.html#modules-as-implementations-of-protocols

# > A module object is accepted where a protocol is expected if the public
# > interface of the given module is compatible with the expected protocol.

import _protocols_modules1
import _protocols_modules2
from typing import Protocol


class Options1(Protocol):
    timeout: int
    one_flag: bool
    other_flag: bool


class Options2(Protocol):
    timeout: str


op1: Options1 = _protocols_modules1  # OK
op2: Options2 = _protocols_modules1  # E


class Reporter1(Protocol):
    def on_error(self, x: int) -> None:
        ...

    def on_success(self) -> None:
        ...


class Reporter2(Protocol):
    def on_error(self, x: int) -> int:
        ...


class Reporter3(Protocol):
    def not_implemented(self, x: int) -> int:
        ...


rp1: Reporter1 = _protocols_modules2  # OK
rp2: Reporter2 = _protocols_modules2  # E
rp3: Reporter3 = _protocols_modules2  # E
