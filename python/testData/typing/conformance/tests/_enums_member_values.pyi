"""
Support stub file for enums_member_values test.
"""

from enum import Enum

# > If the literal values for enum members are not supplied, as they sometimes
# > are not within a type stub file, a type checker can use the type of the
# > _value_ attribute.

class ColumnType(Enum):
    _value_: int
    DORIC = ...
    IONIC = ...
    CORINTHIAN = ...
