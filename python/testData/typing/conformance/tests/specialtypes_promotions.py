"""
Tests "type promotions" for float and complex when they appear in annotations.
"""

# Specification: https://typing.readthedocs.io/en/latest/spec/special-types.html#special-cases-for-float-and-complex

v1: float = 1
v2: complex = 1.2
v2 = 1


def func1(f: float):
    f.numerator  # E

    if not isinstance(f, float):
        f.numerator  # OK
