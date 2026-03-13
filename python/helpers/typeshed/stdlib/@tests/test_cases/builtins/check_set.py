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


def test_set_interface_overlapping_type(s: set[Literal["foo", "bar"]], y: set[str], key: str) -> None:
    s.add(key)  # type: ignore
    s.discard(key)
    s.remove(key)  # type: ignore
    s.difference_update(y)
    s.intersection_update(y)
    s.symmetric_difference_update(y)  # type: ignore
    s.update(y)  # type: ignore

    assert_type(s.difference(y), set[Literal["foo", "bar"]])
    assert_type(s.intersection(y), set[Literal["foo", "bar"]])
    assert_type(s.isdisjoint(y), bool)
    assert_type(s.issubset(y), bool)
    assert_type(s.issuperset(y), bool)
    assert_type(s.symmetric_difference(y), set[str])
    assert_type(s.union(y), set[str])

    assert_type(s - y, set[Literal["foo", "bar"]])
    assert_type(s & y, set[Literal["foo", "bar"]])
    assert_type(s | y, set[str])
    assert_type(s ^ y, set[str])

    s -= y
    s &= y
    s |= y  # type: ignore
    s ^= y  # type: ignore


def test_frozenset_interface(s: frozenset[Literal["foo", "bar"]], y: frozenset[str]) -> None:
    assert_type(s.difference(y), frozenset[Literal["foo", "bar"]])
    assert_type(s.intersection(y), frozenset[Literal["foo", "bar"]])
    assert_type(s.isdisjoint(y), bool)
    assert_type(s.issubset(y), bool)
    assert_type(s.issuperset(y), bool)
    assert_type(s.symmetric_difference(y), frozenset[str])
    assert_type(s.union(y), frozenset[str])

    assert_type(s - y, frozenset[Literal["foo", "bar"]])
    assert_type(s & y, frozenset[Literal["foo", "bar"]])
    assert_type(s | y, frozenset[str])
    assert_type(s ^ y, frozenset[str])
