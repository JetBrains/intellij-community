"""
Tests the Required and NotRequired special forms.
"""

# Specification: https://typing.readthedocs.io/en/latest/spec/typeddict.html#required-and-notrequired

from typing import Annotated, NotRequired, Required, TypedDict


# Required and NotRequired are valid only within a TypedDict.
class NotTypedDict:
    x: Required[int]  # E: Required not allowed in this context

    def __init__(self, x: int) -> None:
        self.x = x


def func1(
    x: NotRequired[int],  # E: NotRequired not allowed in this context
) -> None:
    pass


class TD1(TypedDict, total=False):
    a: int


class TD2(TD1, total=True):
    b: int


class TD3(TypedDict):
    a: NotRequired[int]
    b: Required[int]


class TD4(TypedDict, total=False):
    a: int
    b: Required[int]


class TD5(TypedDict, total=True):
    a: NotRequired[int]
    b: int


td3: TD3 = {"b": 0}
td4: TD4 = {"b": 0}
td5: TD5 = {"b": 0}

# These are all equivalent types, so they should be
# bidirectionally type compatible.
td3 = td4
td3 = td5
td4 = td3
td4 = td5
td5 = td3
td5 = td4


class TD6(TypedDict):
    a: Required[Required[int]]  # E: Nesting not allowed
    b: Required[NotRequired[int]]  # E: Nesting not allowed


class TD7(TypedDict):
    # > Required[] and NotRequired[] can be used with Annotated[], in any nesting order.
    x: Annotated[Required[int], ""]
    y: Required[Annotated[int, ""]]
    z: Annotated[Required[Annotated[int, ""]], ""]


RecursiveMovie = TypedDict(
    "RecursiveMovie", {"title": Required[str], "predecessor": NotRequired["RecursiveMovie"]}
)

movie: RecursiveMovie = {"title": "Beethoven 3", "predecessor": {"title": "Beethoven 2"}}
