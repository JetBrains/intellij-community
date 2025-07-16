import _protocols_modules2
from typing import Protocol


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
rp2: Reporter2 = <warning descr="Expected type 'Reporter2', got '_protocols_modules2.py' instead">_protocols_modules2</warning>
rp3: Reporter3 = <warning descr="Expected type 'Reporter3', got '_protocols_modules2.py' instead">_protocols_modules2</warning>