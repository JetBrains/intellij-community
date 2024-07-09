"""
Tests the handling of __exit__ return types for context managers.
"""

# Specification: https://typing.readthedocs.io/en/latest/spec/exceptions.html


from typing import Any, Literal, assert_type


class CMBase:
    def __enter__(self) -> None:
        pass


class Suppress1(CMBase):
    def __exit__(self, exc_type, exc_value, traceback) -> bool:
        return True


class Suppress2(CMBase):
    def __exit__(self, exc_type, exc_value, traceback) -> Literal[True]:
        return True


class NoSuppress1(CMBase):
    def __exit__(self, exc_type, exc_value, traceback) -> None:
        return None


class NoSuppress2(CMBase):
    def __exit__(self, exc_type, exc_value, traceback) -> Literal[False]:
        return False


class NoSuppress3(CMBase):
    def __exit__(self, exc_type, exc_value, traceback) -> Any:
        return False


class NoSuppress4(CMBase):
    def __exit__(self, exc_type, exc_value, traceback) -> None | bool:
        return None


def suppress1(x: int | str) -> None:
    if isinstance(x, int):
        with Suppress1():
            raise ValueError
    assert_type(x, int | str)


def suppress2(x: int | str) -> None:
    if isinstance(x, int):
        with Suppress2():
            raise ValueError
    assert_type(x, int | str)


def no_suppress1(x: int | str) -> None:
    if isinstance(x, int):
        with NoSuppress1():
            raise ValueError
    assert_type(x, str)


def no_suppress2(x: int | str) -> None:
    if isinstance(x, int):
        with NoSuppress2():
            raise ValueError
    assert_type(x, str)


def no_suppress3(x: int | str) -> None:
    if isinstance(x, int):
        with NoSuppress3():
            raise ValueError
    assert_type(x, str)


def no_suppress4(x: int | str) -> None:
    if isinstance(x, int):
        with NoSuppress4():
            raise ValueError
    assert_type(x, str)
