from typing_extensions import Literal, assert_type


# Note: type checkers / linters are free to point out that the set difference
#   below is redundant. But typeshed should allow it, as its job is to describe
#   what is legal in Python, not what is sensible.
#   For instance, set[Literal] - set[str] should be legal.
def test_set_difference(x: set[Literal["foo", "bar"]], y: set[str], z: set[int]) -> None:
    assert_type(x - y, set[Literal["foo", "bar"]])
    assert_type(y - x, set[str])
    assert_type(x - z, set[Literal["foo", "bar"]])
    assert_type(z - x, set[int])
    assert_type(y - z, set[str])
    assert_type(z - y, set[int])
