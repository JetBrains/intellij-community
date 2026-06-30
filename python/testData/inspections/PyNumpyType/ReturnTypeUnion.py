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


def expect_int_or_str(x: int | str):  ...
def expect_list_or_tuple(x: list[int] | tuple[int, int]):  ...

expect_int_or_str(test_explicit_union())
expect_int_or_str(test_implicit_union())
expect_list_or_tuple(test_union_with_parameterized())
expect_int_or_str(test_pep604_union())
