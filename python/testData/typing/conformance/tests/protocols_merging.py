"""
Tests merging and extending of protocols.
"""

# Specification: https://typing.readthedocs.io/en/latest/spec/protocol.html#merging-and-extending-protocols

from abc import abstractmethod
from typing import Protocol
from collections.abc import Sized


class SizedAndClosable1(Sized, Protocol):
    def close(self) -> None:
        ...


class SizedAndClosable2(Protocol):
    def __len__(self) -> int:
        ...

    def close(self) -> None:
        ...


class SizedAndClosable3(SizedAndClosable1):  # Note: not a protocol
    def __len__(self) -> int:
        return 0

    def close(self) -> None:
        pass


class SCConcrete1:
    def __len__(self) -> int:
        return 0

    def close(self) -> None:
        pass


class SCConcrete2:
    def close(self) -> None:
        pass


s1: SizedAndClosable1 = SCConcrete1()  # OK
s2: SizedAndClosable2 = SCConcrete1()  # OK
s3: SizedAndClosable1 = SizedAndClosable3()  # OK
s4: SizedAndClosable2 = SizedAndClosable3()  # OK
s5: Sized = SCConcrete1()  # OK

s6: SizedAndClosable1 = SCConcrete2()  # E: doesn't implement `__len__`
s7: SizedAndClosable2 = SCConcrete2()  # E: doesn't implement `__len__`
s8: SizedAndClosable3 = SCConcrete2()  # E: SizedAndClosable3 is not a protocol


def func1(s1: SizedAndClosable1, s2: SizedAndClosable2):
    # > The two definitions of SizedAndClosable are equivalent.
    v1: SizedAndClosable2 = s1
    v2: SizedAndClosable1 = s2


# > If Protocol is included in the base class list, all the other base classes
# > must be protocols.


class BadProto(SizedAndClosable3, Protocol):  # E: SizedAndClosable3 is not a protocol
    ...


# > Without this base, the class is “downgraded” to a regular ABC that
# > cannot be used with structural subtyping.
class SizedAndClosable4(SizedAndClosable1):
    def __len__(self) -> int:
        return 0

    @abstractmethod
    def close(self) -> None:
        raise NotImplementedError


x = SizedAndClosable4()  # E: cannot instantiate abstract class
y: SizedAndClosable4 = SCConcrete1()  # E
