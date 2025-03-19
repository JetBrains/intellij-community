"""
Tests NamedTuple definitions using the functional syntax.
"""

from collections import namedtuple
from typing import NamedTuple

# Specification: https://typing.readthedocs.io/en/latest/spec/namedtuples.html#defining-named-tuples

# > A type checker may support the function syntax in its various forms::


Point1 = namedtuple("Point1", ["x", "y"])
p1_1 = Point1(x=1, y=1)
p1_2 = Point1(2.3, "")
p1_3 = Point1(2.3)  # E

Point2 = namedtuple("Point2", ("x", "y"))
p2_1 = Point2(x=1, y=1)
p2_2 = Point2(2.3, "")
p2_3 = Point2()  # E

Point3 = namedtuple("Point3", "x y")
p3_1 = Point3(x=1, y=1)
p3_2 = Point3(2.3, "")
p3_3 = Point3(1, 2, 3)  # E

Point4 = namedtuple("Point4", "x, y")
p4_1 = Point4(x=1, y=1)
p4_2 = Point4(2.3, "")
p4_3 = Point4(1, z=3)  # E

Point5 = NamedTuple("Point5", [("x", int), ("y", int)])
p5_1 = Point5(x=1, y=1)
p5_2 = Point5(2, 1)
p5_3 = Point5(2, "1")  # E
p5_4 = Point5(1, 2, 3)  # E

Point6 = NamedTuple("Point6", (("x", int), ("y", int)))
p6_1 = Point6(x=1, y=1)
p6_2 = Point6(2, 1)
p6_3 = Point6(2, "1")  # E
p6_4 = Point6(x=1.1, y=2)  # E


# > At runtime, the ``namedtuple`` function disallows field names that are
# > illegal Python identifiers and either raises an exception or replaces these
# > fields with a parameter name of the form ``_N``. The behavior depends on
# > the value of the ``rename`` argument. Type checkers may replicate this
# > behavior statically.

NT1 = namedtuple("NT1", ["a", "a"])  # E?: duplicate field name
NT2 = namedtuple("NT2", ["abc", "def"])  # E?: illegal field name
NT3 = namedtuple("NT3", ["abc", "def"], rename=False)  # E?: illegal field name

NT4 = namedtuple("NT4", ["abc", "def"], rename=True)  # OK
NT4(abc="", _1="")  # OK


# > The ``namedtuple`` function also supports a ``defaults`` keyword argument that
# > specifies default values for the fields. Type checkers may support this.

NT5 = namedtuple("NT5", "a b c", defaults=(1, 2))
NT5(1)  # OK
NT5(1, 2, 3)  # OK
NT5()  # E: too few arguments
