from typing import Callable


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


def expect_callable(cb: Callable[[str, str, str], str]):  ...
def expect_int(x: int):  ...

expect_callable(test_explicit_type())
expect_callable(test_explicit_type_comment())
expect_int(test_numpy_docstring())
