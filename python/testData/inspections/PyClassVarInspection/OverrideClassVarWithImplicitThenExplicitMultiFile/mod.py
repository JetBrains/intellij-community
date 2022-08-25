from typing import ClassVar
class A:
    x = 1  # type: ClassVar[int]
class B(A):
    x = 2
class C(B):
    x = 3
