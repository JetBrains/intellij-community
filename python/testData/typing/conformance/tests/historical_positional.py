"""
Tests the historical (pre-3.8) mechanism for specifying positional-only
parameters in function signatures.
"""

# Specification: https://typing.readthedocs.io/en/latest/spec/historical.html#positional-only-parameters


# > Type checkers should support the following special case: all parameters with
# > names that begin but don’t end with __ are assumed to be positional-only:


def f1(__x: int, __y__: int = 0) -> None: ...


f1(3, __y__=1)  # OK

f1(__x=3)  # E


# > Consistent with PEP 570 syntax, positional-only parameters cannot appear
# > after parameters that accept keyword arguments. Type checkers should
# > enforce this requirement:


def f2(x: int, __y: int) -> None: ...  # E


# `x` is a positional-or-keyword parameter, so,
# according to a literal reading of the above rule, `__y`
# should be flagged as an error since it uses the historical
# convention for positional-only parameters but appears after
# a positional-or-keyword parameter that does not. We therefore
# permit type checkers to emit an error on this definition.
#
# Note that `x` can only be passed with a keyword argument if
# no arguments are passed positionally:
#
# ```pycon
# >>> def f3(x: int, *args: int, __y: int) -> None: ...
# ...
# >>> f3(x=1, __y=2)
# >>>
# ```
def f3(x: int, *args: int, __y: int) -> None: ...  # E?


f3(3, __y=3)  # OK


class A:
    def m1(self, __x: int, __y__: int = 0) -> None: ...

    def m2(self, x: int, __y: int) -> None: ...  # E


a = A()
a.m1(3, __y__=1)  # OK
a.m1(__x=3)  # E


# The historical mechanism should not apply when new-style (PEP 570)
# syntax is used.


def f4(x: int, /, __y: int) -> None: ...  # OK


f4(3, __y=4)  # OK
