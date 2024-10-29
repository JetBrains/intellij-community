"""
Tests consistency checks between __new__ and __init__ methods.
"""

# pyright: reportInconsistentConstructor=true

# Specification: https://typing.readthedocs.io/en/latest/spec/constructors.html#consistency-of-new-and-init

# Note: This functionality is optional in the typing spec, and conformant
# type checkers are not required to implement it.


# > Type checkers may optionally validate that the __new__ and __init__
# > methods for a class have consistent signatures.


from typing import Self


class Class1:
    def __new__(cls) -> Self:
        return super().__new__(cls)

    # Type error: __new__ and __init__ have inconsistent signatures
    def __init__(self, x: str) -> None:  # E?
        pass
