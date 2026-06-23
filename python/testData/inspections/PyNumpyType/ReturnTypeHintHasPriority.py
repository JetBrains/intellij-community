from typing import Callable, assert_type


def test_explicit_type() -> Callable[[str, str, str], str]:
    """
    Returns
    -------
    int
        Docstring type differs from the explicit annotation.
    """
    ...


def test_explicit_type_comment():
    # type: () -> Callable[[str, str, str], str]
    """
    Returns
    -------
    int
        Docstring type differs from the explicit annotation.
    """
    ...


def test_numpy_docstring():
    """
    Returns
    -------
    int
        Docstring type differs from the explicit annotation.
    """
    ...


assert_type(test_explicit_type(), Callable[[str, str, str], str])
assert_type(test_explicit_type_comment(), Callable[[str, str, str], str])
assert_type(test_numpy_docstring(), int)
