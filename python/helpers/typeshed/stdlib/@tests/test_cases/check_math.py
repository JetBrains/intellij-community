from __future__ import annotations

from decimal import Decimal
from fractions import Fraction
from math import prod
from typing import Any, Literal, Union
from typing_extensions import assert_type


class SupportsMul:
    def __mul__(self, other: Any) -> SupportsMul:
        return SupportsMul()


class SupportsRMul:
    def __rmul__(self, other: Any) -> SupportsRMul:
        return SupportsRMul()


class SupportsMulAndRMul:
    def __mul__(self, other: Any) -> SupportsMulAndRMul:
        return SupportsMulAndRMul()

    def __rmul__(self, other: Any) -> SupportsMulAndRMul:
        return SupportsMulAndRMul()


literal_list: list[Literal[0, 1]] = [0, 1, 1]

assert_type(prod([2, 4]), int)
assert_type(prod([3, 5], start=4), int)

assert_type(prod([True, False]), int)
assert_type(prod([True, False], start=True), int)
assert_type(prod(literal_list), int)

assert_type(prod([SupportsMul(), SupportsMul()], start=SupportsMul()), SupportsMul)
assert_type(prod([SupportsMulAndRMul(), SupportsMulAndRMul()]), Union[SupportsMulAndRMul, Literal[1]])

assert_type(prod([5.6, 3.2]), Union[float, Literal[1]])
assert_type(prod([5.6, 3.2], start=3), Union[float, int])

assert_type(prod([Fraction(7, 2), Fraction(3, 5)]), Union[Fraction, Literal[1]])
assert_type(prod([Fraction(7, 2), Fraction(3, 5)], start=Fraction(1)), Fraction)
assert_type(prod([Decimal("3.14"), Decimal("2.71")]), Union[Decimal, Literal[1]])
assert_type(prod([Decimal("3.14"), Decimal("2.71")], start=Decimal("1.00")), Decimal)
assert_type(prod([complex(7, 2), complex(3, 5)]), Union[complex, Literal[1]])
assert_type(prod([complex(7, 2), complex(3, 5)], start=complex(1, 0)), complex)


# mypy and pyright infer the types differently for these, so we can't use assert_type
# Just test that no error is emitted for any of these
prod([5.6, 3.2])  # mypy: `float`; pyright: `float | Literal[0]`
prod([2.5, 5.8], start=5)  # mypy: `float`; pyright: `float | int`

# These all fail at runtime
prod([SupportsMul(), SupportsMul()])  # type: ignore
prod([SupportsRMul(), SupportsRMul()], start=SupportsRMul())  # type: ignore
prod([SupportsRMul(), SupportsRMul()])  # type: ignore

# TODO: these pass pyright with the current stubs, but mypy erroneously emits an error:
# prod([3, Fraction(7, 22), complex(8, 0), 9.83])
# prod([3, Decimal("0.98")])
