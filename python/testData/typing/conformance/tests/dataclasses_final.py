"""
Tests the handling of ClassVar and Final in dataclasses.
"""

from dataclasses import dataclass
from typing import assert_type, ClassVar, Final

# Specification: https://typing.readthedocs.io/en/latest/spec/dataclasses.html#dataclass-semantics

# > A final class variable on a dataclass must be explicitly annotated as
# e.g. x: ClassVar[Final[int]] = 3.


@dataclass
class D:
    final_no_default: Final[int]
    final_with_default: Final[str] = "foo"
    final_classvar: ClassVar[Final[int]] = 4
    # we don't require support for Final[ClassVar[...]] because the dataclasses
    # runtime implementation won't recognize it as a ClassVar either


# An explicitly marked ClassVar can be accessed on the class:
assert_type(D.final_classvar, int)

# ...but not assigned to, because it's Final:
D.final_classvar = 10  # E: can't assign to final attribute

# A non-ClassVar attribute (with or without default) is a dataclass field:
d = D(final_no_default=1, final_with_default="bar")
assert_type(d.final_no_default, int)
assert_type(d.final_with_default, str)

# ... but can't be assigned to (on the class or on an instance):
d.final_no_default = 10  # E: can't assign to final attribute
d.final_with_default = "baz"  # E: can't assign to final attribute
D.final_no_default = 10  # E: can't assign to final attribute
D.final_with_default = "baz"  # E: can't assign to final attribute
