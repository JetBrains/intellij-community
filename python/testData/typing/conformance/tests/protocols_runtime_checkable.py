"""
Tests the handling of the @runtime_checkable decorator for protocols.
"""

# Specification: https://typing.readthedocs.io/en/latest/spec/protocol.html#runtime-checkable-decorator-and-narrowing-types-by-isinstance

# > A protocol can be used as a second argument in isinstance() and
# > issubclass() only if it is explicitly opt-in by @runtime_checkable decorator.

from typing import Any, Protocol, runtime_checkable


class Proto1(Protocol):
    name: str


@runtime_checkable
class Proto2(Protocol):
    name: str


def func1(a: Any):
    if isinstance(a, Proto1):  # E: not runtime_checkable
        return

    if isinstance(a, Proto2):  # OK
        return


# > isinstance() can be used with both data and non-data protocols, while
# > issubclass() can be used only with non-data protocols.


@runtime_checkable
class DataProtocol(Protocol):
    name: str

    def method1(self) -> int:
        ...


@runtime_checkable
class NonDataProtocol(Protocol):
    def method1(self) -> int:
        ...


def func2(a: Any):
    if isinstance(a, DataProtocol):  # OK
        return

    if isinstance(a, NonDataProtocol):  # OK
        return

    if issubclass(a, DataProtocol):  # E
        return

    if issubclass(a, NonDataProtocol):  # OK
        return

    if issubclass(a, (NonDataProtocol, DataProtocol)):  # E
        return


# > Type checkers should reject an isinstance() or issubclass() call if there
# > is an unsafe overlap between the type of the first argument and the protocol.


@runtime_checkable
class Proto3(Protocol):
    def method1(self, a: int) -> int:
        ...


class Concrete3A:
    def method1(self, a: str) -> None:
        pass


class Concrete3B:
    method1: int = 1


def func3():
    if isinstance(Concrete3A(), Proto2):  # OK
        pass

    if isinstance(Concrete3A(), Proto3):  # E: unsafe overlap
        pass

    if isinstance(
        Concrete3B(), (Proto3, NonDataProtocol)  # E: unsafe overlap
    ):
        pass

    if issubclass(Concrete3A, (Proto3, NonDataProtocol)):  # E: unsafe overlap
        pass
