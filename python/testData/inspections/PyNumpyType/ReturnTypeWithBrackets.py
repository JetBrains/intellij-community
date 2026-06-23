from typing import Callable, assert_type


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


assert_type(test_callable_from_docstring(), Callable[[str, str, str], str])
assert_type(test_triple_tuple_from_docstring(), tuple[int, int, int])
