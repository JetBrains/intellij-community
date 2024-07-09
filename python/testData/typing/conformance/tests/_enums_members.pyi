"""
Support stub file for enums_members test.
"""

from enum import Enum

# > Within a type stub, members can be defined using the actual runtime values,
# > or a placeholder of ... can be used

class Pet2(Enum):
    genus: str  # Non-member attribute
    species: str  # Non-member attribute

    CAT = ...  # Member attribute
    DOG = ...  # Member attribute
