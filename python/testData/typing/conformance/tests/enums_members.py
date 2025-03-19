"""
Tests that the type checker can distinguish enum members from non-members.
"""

# Specification: https://typing.readthedocs.io/en/latest/spec/enums.html#defining-members

from enum import Enum, member, nonmember
from typing import Literal, assert_type, reveal_type

# > If an attribute is defined in the class body with a type annotation but
# > with no assigned value, a type checker should assume this is a non-member
# > attribute


class Pet(Enum):  # E?: Uninitialized attributes (pyre)
    genus: str  # Non-member attribute
    species: str  # Non-member attribute

    CAT = 1  # Member attribute
    DOG = 2  # Member attribute


assert_type(Pet.genus, str)
assert_type(Pet.species, str)
assert_type(Pet.CAT, Literal[Pet.CAT])
assert_type(Pet.DOG, Literal[Pet.DOG])


from _enums_members import Pet2

assert_type(Pet2.genus, str)
assert_type(Pet2.species, str)
assert_type(Pet2.CAT, Literal[Pet2.CAT])
assert_type(Pet2.DOG, Literal[Pet2.DOG])


# > Members defined within an enum class should not include explicit type
# > annotations. Type checkers should infer a literal type for all members.
# > A type checker should report an error if a type annotation is used for
# > an enum member because this type will be incorrect and misleading to
# > readers of the code


class Pet3(Enum):
    CAT = 1
    DOG: int = 2  # E


# > Methods, callables, descriptors (including properties), and nested classes
# > that are defined in the class are not treated as enum members by the
# > EnumType metaclass and should likewise not be treated as enum members by a
# > type checker


def identity(x: int) -> int:
    return x


class Pet4(Enum):
    CAT = 1  # Member attribute
    DOG = 2  # Member attribute

    converter = lambda x: str(x)  # Non-member attribute
    transform = staticmethod(identity)  # Non-member attribute

    @property
    def species(self) -> str:  # Non-member property
        return "mammal"

    def speak(self) -> None:  # Non-member method
        print("meow" if self is Pet.CAT else "woof")

    class Nested: ...  # Non-member nested class


assert_type(Pet4.CAT, Literal[Pet4.CAT])
assert_type(Pet4.DOG, Literal[Pet4.DOG])
assert_type(Pet4.converter, Literal[Pet4.converter])  # E
assert_type(Pet4.transform, Literal[Pet4.transform])  # E
assert_type(Pet4.species, Literal[Pet4.species])  # E
assert_type(Pet4.speak, Literal[Pet4.speak])  # E


# > An attribute that is assigned the value of another member of the same
# > enum is not a member itself. Instead, it is an alias for the first member


class TrafficLight(Enum):
    RED = 1
    GREEN = 2
    YELLOW = 3

    AMBER = YELLOW  # Alias for YELLOW


assert_type(TrafficLight.AMBER, Literal[TrafficLight.YELLOW])

# > If using Python 3.11 or newer, the enum.member and enum.nonmember classes
# > can be used to unambiguously distinguish members from non-members.


class Example(Enum):
    a = member(1)  # Member attribute
    b = nonmember(2)  # Non-member attribute

    @member
    def c(self) -> None:  # Member method
        pass


assert_type(Example.a, Literal[Example.a])
assert_type(Example.b, Literal[Example.b])  # E
assert_type(Example.c, Literal[Example.c])


# > An attribute with a private name (beginning with, but not ending in,
# > a double underscore) is treated as a non-member.


class Example2(Enum):
    __B = 2  # Non-member attribute

    def method(self):
        reveal_type(Example2.__B)
        assert_type(Example2.__B, Literal[Example2.__B])  # E


# > An enum class can define a class symbol named _ignore_. This can be
# > a list of names or a string containing a space-delimited list of names
# > that are deleted from the enum class at runtime. Type checkers may
# > support this mechanism


class Pet5(Enum):
    _ignore_ = "DOG FISH"
    CAT = 1  # Member attribute
    DOG = 2  # temporary variable, will be removed from the final enum class
    FISH = 3  # temporary variable, will be removed from the final enum class


assert_type(Pet5.CAT, Literal[Pet5.CAT])
assert_type(Pet5.DOG, int)  # E?: Literal[2] is also acceptable
assert_type(Pet5.FISH, int)  # E?: Literal[3] is also acceptable
