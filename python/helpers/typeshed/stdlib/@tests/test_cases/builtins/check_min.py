from typing_extensions import assert_type


def test_min_builtin() -> None:
    # legal comparisons that succeed at runtime
    b1, b2 = bool(True), bool(False)
    i1, i2 = int(1), int(2)
    s1, s2 = str("a"), str("b")
    f1, f2 = float(0.5), float(2.3)
    l1, l2 = list[int]([1, 2]), list[int]([3, 4])
    t1, t2 = tuple[str, str](("A", "B")), tuple[str, str](("C", "D"))
    tN = tuple[str, ...](["A", "B", "C"])

    assert_type(min(b1, b2), bool)
    assert_type(min(i1, i2), int)
    assert_type(min(s1, s2), str)
    assert_type(min(f1, f2), float)

    # mixed numerical types (note: float = int or float)
    assert_type(min(b1, i1), int)
    assert_type(min(i1, b1), int)

    assert_type(min(b1, f1), float)
    assert_type(min(f1, b1), float)

    assert_type(min(i1, f1), float)
    assert_type(min(f1, i1), float)

    # comparisons with lists and tuples
    assert_type(min(l1, l2), list[int])
    assert_type(min(t1, t2), tuple[str, str])
    assert_type(min(tN, t2), tuple[str, ...])


def test_min_bad_builtin() -> None:
    # illegal comparisons that fail at runtime
    i1 = int(1)
    s1 = str("a")
    f1 = float(1.0)
    c1, c2 = complex(1.0, 2.0), complex(3.0, 4.0)
    list_str = list[str](["A", "B"])
    list_int = list[int]([2, 3])
    tup_str = tuple[str, str](("A", "B"))
    tup_int = tuple[int, int]((2, 3))

    # True negatives.
    min(c1, c2)  # type: ignore

    # FIXME: False negatives.
    min(i1, s1)
    min(s1, f1)
    min(f1, list_str)
    min(list_str, list_int)
    min(tup_str, tup_int)


def test_min_custom_comparison() -> None:
    class BoolScalar:
        def __bool__(self) -> bool: ...

    class FloatScalar:
        def __float__(self) -> float: ...
        def __ge__(self, other: "FloatScalar") -> BoolScalar: ...
        def __gt__(self, other: "FloatScalar") -> BoolScalar: ...
        def __lt__(self, other: "FloatScalar") -> BoolScalar: ...
        def __le__(self, other: "FloatScalar") -> BoolScalar: ...

    f1 = FloatScalar()
    f2 = FloatScalar()

    assert_type(min(f1, f2), FloatScalar)


def test_min_bad_custom_type() -> None:
    class FloatScalar:
        def __float__(self) -> float: ...
        def __ge__(self, other: "FloatScalar") -> object:
            return object()

        def __gt__(self, other: "FloatScalar") -> object:
            return object()

        def __lt__(self, other: "FloatScalar") -> object:
            return object()

        def __le__(self, other: "FloatScalar") -> object:
            return object()

    f1 = FloatScalar()
    f2 = FloatScalar()

    # Note: min(f1, f2) works at runtime, but always returns the second argument.
    #   therefore, we require returning a boolean-like type for comparisons.
    min(f1, f2)  # type: ignore
