from typing import assert_type


def test_explicit_union():
    """
    Returns
    -------
    {int, str}
    """
    ...


def test_implicit_union():
    """
    Returns
    -------
    int, str
    """
    ...


def test_union_with_parameterized():
    """
    Returns
    -------
    {list[int], tuple[int, int]}
    """
    ...


def test_pep604_union():
    """
    Returns
    -------
    int | str
    """
    ...


assert_type(test_explicit_union(), int | str)
assert_type(test_implicit_union(), int | str)
assert_type(test_union_with_parameterized(), list[int] | tuple[int, int])
assert_type(test_pep604_union(), int | str)
