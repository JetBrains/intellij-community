from typing import Callable


def test_callable_from_docstring():
    """
    Returns
    -------
    Callable[[str, str, str], str]
    """
    ...


def test_triple_tuple_from_docstring():
    """
    Returns
    -------
    tuple[int, int, int]
    """
    ...


def expect_callable(cb: Callable[[str, str, str], str]):  ...
def expect_triple_tuple(t: tuple[int, int, int]):  ...

expect_callable(test_callable_from_docstring())
expect_triple_tuple(test_triple_tuple_from_docstring())
