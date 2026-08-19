"""
Tests variance inference for mixed type parameters.
"""

# Specification: https://typing.readthedocs.io/en/latest/spec/generics.html#variance-inference

class Mixed[T, *Ts, **P]:
    def f(self, x: T, /, *args: P.args, **kwargs: P.kwargs) -> tuple[*Ts]:
        raise NotImplementedError

# T should be contra
_1: Mixed[int, []] = Mixed[object, []]()  # OK
_2: Mixed[int, []] = Mixed[bool, []]()  # E

# Ts should be co
_3: Mixed[int, int, []] = Mixed[int, object, []]()  # E
_4: Mixed[int, int, []] = Mixed[int, bool, []]()  # OK

# P should be contra
_5: Mixed[int, [int]] = Mixed[int, [object]]()  # OK
_6: Mixed[int, [int]] = Mixed[int, [bool]]()  # E
