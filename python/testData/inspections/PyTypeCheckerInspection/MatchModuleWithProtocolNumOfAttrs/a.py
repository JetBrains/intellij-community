import _protocols_modules1
from typing import Protocol


class Options1(Protocol):
    timeout: T
    one_flag: bool
    other_flag: bool

class Options2(Protocol):
    timeout: str



op1: Options1 = _protocols_modules1
op1: Options2 = <warning descr="Expected type 'Options2', got '_protocols_modules1.py' instead">_protocols_modules1</warning>