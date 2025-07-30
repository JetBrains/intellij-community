// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python;

import com.jetbrains.python.fixtures.PyInspectionTestCase;
import com.jetbrains.python.inspections.PyAssertTypeInspection;
import com.jetbrains.python.inspections.PyInspection;
import org.jetbrains.annotations.NotNull;

public class PyPatternTypeTest extends PyInspectionTestCase {

  @Override
  protected @NotNull Class<? extends PyInspection> getInspectionClass() {
    return PyAssertTypeInspection.class;
  }

  @Override
  protected String getTestCaseDirectory() {
    return "inspections/PyPatternTypeTest/";
  }
  
  public void testPyrightMatchClass1() {
    // matchClass1.py test from pyright repo, adjusted to our system.
    // Parts that are commented out are WIP
    doTest();
  }

  public void testMatchCapturePatternType() {
    doTestByText("""
from typing import assert_type
class A: ...
m: A

match m:
    case a:
        assert_type(a, A)
        """);
  }

  public void testMatchLiteralPatternNarrows() {
    doTestByText("""
from typing import assert_type, Literal
m: object

match m:
    case 1:
        assert_type(m, Literal[1])
        """);
  }

  public void testMatchLiteralPatternNarrowsWalrus() {
    doTestByText("""
from typing import assert_type, Literal

match m := input():
    case "one" | "two":
        assert_type(m, Literal["one", "two"])
        """);
  }

  public void testMatchValuePatternNarrows() {
    doTestByText("""
from typing import assert_type
class B:
    b: int

m: object

match m:
    case B.b:
        assert_type(m, int)
        """);
  }

  public void testMatchValuePatternAlreadyNarrower() {
    doTestByText("""
from typing import assert_type
class B:
    b: int
m: bool

match m:
    case B.b:
        assert_type(m, bool)
        """);
  }

  public void testMatchSequencePatternCaptures() {
    doTestByText("""
from typing import assert_type
m: list[int]

match m:
    case [a]:
        assert_type(a, int)
        """);
  }

  public void testMatchSequencePatternCapturesStarred() {
    doTestByText("""
from typing import assert_type
from typing import Sequence
m: Sequence[int]

match m:
    case [a, *b]:
        assert_type(a, int)
        assert_type(b, list[int])
        """);
  }

  public void testMatchSequencePatternNarrowsInner() {
    doTestByText("""
from typing import assert_type
from typing import Sequence
m: Sequence[object]

match m:
    case [1, True]:
        assert_type(m, Sequence[int | bool])
        """);
  }

  public void testMatchSequencePatternNarrowsOuter() {
    doTestByText("""
from typing import assert_type
from typing import Sequence
m: object

match m:
    case [1, True]:
        assert_type(m, Sequence[int | bool])
        """);
  }

  public void testMatchSequencePatternAlreadyNarrowerInner() {
    doTestByText("""
from typing import assert_type
from typing import Sequence
m: Sequence[bool]

match m:
    case [1, True]:
        assert_type(m, Sequence[bool])
        """);
  }

  public void testMatchSequencePatternAlreadyNarrowerOuter() {
    doTestByText("""
from typing import assert_type
from typing import Sequence
m: Sequence[object]

match m:
    case [1, True]:
        assert_type(m, Sequence[int | bool])
        """);
  }

  public void testMatchSequencePatternAlreadyNarrowerBoth() {
    doTestByText("""
from typing import assert_type
from typing import Sequence
m: Sequence[bool]

match m:
    case [1, True]:
        assert_type(m, Sequence[bool])
        """);
  }

  public void testMatchSequencePatternNarrowSubjectItems() {
    doTestByText("""
from typing import assert_type, Literal
m: int
n: str
o: bool

match m, n, o:
    case [3, "foo", True]:
        assert_type(m, Literal[3])
        assert_type(n, Literal['foo'])
        assert_type(o, Literal[True])
    case [a, b, c]:
        assert_type(m, int)
        assert_type(n, str)
        assert_type(o, bool)
        """);
  }

  public void testMatchSequencePatternNarrowSubjectItemsRecursive() {
    doTestByText("""
from typing import assert_type, Literal
m: int
n: int
o: int
p: int
q: int
r: int

match m, (n, o), (p, (q, r)):
    case [0, [1, 2], [3, [4, 5]]]:
        assert_type(m, Literal[0])
        assert_type(n, Literal[1])
        assert_type(o, Literal[2])
        assert_type(p, Literal[3])
        assert_type(q, Literal[4])
        assert_type(r, Literal[5])
        """);
  }

  public void testMatchSequencePatternSequencesLengthMismatchNoNarrowing() {
    doTestByText("""
m: int
n: str
o: bool

match m, n, o:
    case [3, "foo"]:
        assert_type(m, int)
        assert_type(n, str)
        assert_type(o, bool)
    case [3, "foo", True, True]:
        assert_type(m, int)
        assert_type(n, str)
        assert_type(o, bool)
        """);
  }

  public void testMatchNestedSequencePatternNarrowsInner() {
    doTestByText("""
from typing import assert_type
from typing import Sequence
m: Sequence[Sequence[object]]

match m:
    case [[1], [True]]:
        assert_type(m, Sequence[Sequence[int] | Sequence[bool]])
        """);
  }

  public void testMatchNestedSequencePatternNarrowsOuter() {
    doTestByText("""
from typing import assert_type
from typing import Sequence
m: object

match m:
    case [[1], [True]]:
        assert_type(m, Sequence[Sequence[int] | Sequence[bool]])
        """);
  }

  public void testMatchSequencePatternMatches() {
    doTestByText("""
from typing import assert_type
import array, collections
from typing import Sequence, Iterable

m1: object
m2: Sequence[int]
m3: array.array[int]
m4: collections.deque[int]
m5: list[int]
m6: memoryview
m7: range
m8: tuple[int]

m9: str
m10: bytes
m11: bytearray

match m1:
    case [a]:
        assert_type(a, Any)

match m2:
    case [b]:
        assert_type(b, int)

match m3:
    case [c]:
        assert_type(c, int)

match m4:
    case [d]:
        assert_type(d, int)

match m5:
    case [e]:
        assert_type(e, int)

match m6:
    case [f]:
        assert_type(f, int)

match m7:
    case [g]:
        assert_type(g, int)

match m8:
    case [h]:
        assert_type(h, int)

match m9:
    case [i]:
        assert_type(i, Any)

match m10:
    case [j]:
        assert_type(j, Any)

match m11:
    case [k]:
        assert_type(k, Any)
        """);
  }

  public void testMatchSequencePatternCapturesTuple() {
    doTestByText("""
from typing import assert_type
m: tuple[int, str, bool]

match m:
    case [a, b, c]:
        assert_type(a, int)
        assert_type(b, str)
        assert_type(c, bool)
        assert_type(m, tuple[int, str, bool])
        """);
  }

  public void testMatchSequencePatternTupleNarrows() {
    doTestByText("""
from typing import assert_type, Literal
m: tuple[object, object]

match m:
    case [1, "str"]:
        assert_type(m, tuple[Literal[1], Literal['str']])
        """);
  }

  public void testMatchSequencePatternTupleStarred() {
    doTestByText("""
from typing import assert_type, Literal
m: tuple[int, str, bool]

match m:
    case [a, *b, c]:
        assert_type(a, int)
        assert_type(b, list[str])
        assert_type(c, bool)
        assert_type(m, tuple[int, str, bool])
        """);
  }

  public void testMatchSequencePatternTupleStarredUnion() {
    doTestByText("""
from typing import assert_type
m: tuple[int, str, float, bool]

match m:
    case [a, *b, c]:
        assert_type(a, int)
        assert_type(b, list[str | float])
        assert_type(c, bool)
        assert_type(m, tuple[int, str, float, bool])
        """);
  }

  public void testMatchSequenceUnionSkip() {
    doTestByText("""
from typing import assert_type
from typing import List, Union
m: Union[List[List[str]], str]

match m:
    case [list(['str'])]:
        assert_type(m, list[list[str]])
        """);
  }
  
  public void testMatchSequenceNotNamedElement() {
    doTestByText("""
from typing import assert_type
def func():
    match [10]:
        case [*values]:
            return values[0]

assert_type(func(), int)
                   """);
  }

  public void testMatchHomogeneousTuplePattern() {
    doTestByText("""
from typing import assert_type
m: tuple[int, ...]

match m:
    case [a, b, c]:
        assert_type(a, int)
        assert_type(b, int)
        assert_type(c, int)
        assert_type(m, tuple[int, ...])

    case [a, b]:
        assert_type(a, int)
        assert_type(b, int)
        assert_type(m, tuple[int, ...])
        """);
  }


  public void testMatchNotHomogeneousTuplePattern() {
    doTestByText("""
from typing import assert_type, Never
m: tuple[int, str]

match m:
    case [a, b]:
        assert_type(a, int)
        assert_type(b, str)
        assert_type(m, tuple[int, str])
    
    case x:
        assert_type(x, Never)
        """);
  }

  public void testMatchMappingPatternCaptures() {
    doTestByText("""
from typing import Dict, assert_type
class B:
    b: str
m: Dict[str, int]

match m:
    case {"key": v}:
        assert_type(v, int)

match m:
    case {B.b: v2}:
        assert_type(v2, int)
                   """);
  }

  public void testMatchMappingPatternCapturesTypedDict() {
    doTestByText("""
from typing import TypedDict, Literal, assert_type

class A(TypedDict):
    a: str
    b: int
    
class K:
    k: Literal['a']

m: A

match m:
    case {"a": v}:
        assert_type(v, str)
    case {"b": v2}:
        assert_type(v2, int)
    case {"a": v3, "b": v4}:
        assert_type(v3, str)
        assert_type(v4, int)
    case {K.k: v5}:
        assert_type(v5, str)
    case {"o": v6}:
        assert_type(v6, Any)
                   """);
  }

  public void testMatchMappingPatternCapturesUnion() {
    doTestByText("""
from typing import TypedDict, Literal, assert_type

class A(TypedDict):
    a: Literal["str"]
    b: Literal[42]


m: A | dict[str | int, int]

match m:
    case {"a": v}:
        assert_type(v, Literal["str"] | int)
    case {"b": v2}:
        assert_type(v2, Literal[42] | int)
    case {"a": v3, "b": v4}:
        assert_type(v3, Literal["str"] | int)
        assert_type(v4, Literal[42] | int)
    case {42: v5}:
        assert_type(v5, int)
                   """);
  }

  public void testMatchMappingPatternCaptureRest() {
    doTestByText("""
from typing import Mapping, assert_type

m: object

match m:
    case {'k': 1, **r}:
        assert_type(r, dict[str, int])
        
n: Mapping[str, int]

match n:
    case {'k': 1, **r}:
        assert_type(r, dict[str, int])
                   """);
  }

  public void testMatchClassPatternCapturePositional() {
    doTestByText("""
from typing import assert_type

class A:
    __match_args__ = ("a", "b")
    a: str
    b: int

m: A

match m:
    case A(i, j):
        assert_type(i, str)
        assert_type(j, int)
                   """);
  }

  public void testMatchClassPatternCaptureKeyword() {
    doTestByText("""
from typing import assert_type

class A:
    a: str
    b: int

m: A

match m:
    case A(a="name", b=j):
        assert_type(j, int)
        
    case A():
        assert_type(m, A)
                   """);
  }

  // PY-79716
  public void testMatchClassPatternShadowingCapture() {
    doTestByText("""
from typing import assert_type

class C:
    foo: str
    bar: int

def f(x):
    match x:
        case C(foo=x, bar=y):
            assert_type(x, str)
                   """);
  }

  // PY-82963
  public void testMatchClassPatternShadowingCaptureMultipleSubjects() {
    doTestByText("""
from typing import assert_type

class C:
    foo: str
    bar: int

def f(a, b):
    match a, b:
        case C(foo=a), C():
            assert_type(a, str)
            assert_type(b, C)
                   """);
  }
  
  public void testMatchClassPatternNegativeNarrow() {
      doTestByText("""
from typing import assert_type, Never

class A:
    a: str
    b: int

m: A

match m:
    case A(a=i, b=j):
        assert_type(j, int)
        
    case A():
        assert_type(m, Never)
                   """);
    }

  public void testMatchClassPatternCaptureSelf() {
    doTestByText("""
from typing import assert_type

m: object

match m:
    case bool(a):
        assert_type(a, bool)
    case bytearray(b):
        assert_type(b, bytearray)
    case bytes(c):
        assert_type(c, bytes)
    case dict(d):
        assert_type(d, dict)
    case float(e):
        assert_type(e, float)
    case frozenset(f):
        assert_type(f, frozenset)
    case int(g):
        assert_type(g, int)
    case list(h):
        assert_type(h, list)
    case set(i):
        assert_type(i, set)
    case str(j):
        assert_type(j, str)
    case tuple(k):
        assert_type(k, tuple)
                   """
    );
  }

  public void testMatchClassPatternNarrowSelfCapture() {
    doTestByText("""
 from typing import assert_type

 m: object

 match m:
     case bool():
         assert_type(m, bool)
     case bytearray():
         assert_type(m, bytearray)
     case bytes():
         assert_type(m, bytes)
     case dict():
         assert_type(m, dict)
     case float():
         assert_type(m, float)
     case frozenset():
         assert_type(m, frozenset)
     case int():
         assert_type(m, int)
     case list():
         assert_type(m, list)
     case set():
         assert_type(m, set)
     case str():
         assert_type(m, str)
     case tuple():
         assert_type(m, tuple)"""
    );
  }

  public void testMatchClassPatternCapture() {
    doTestByText("""
from typing import assert_type

class A:
    __match_args__ = ("a", "b")
    a: str
    b: int

m: A

match m:
    case A(i, j):
        assert_type(i, str)
        assert_type(j, int)
                   """);
  }

  public void testMatchClassPatternCaptureDataclass() {
    doTestByText("""
from dataclasses import dataclass
from typing import assert_type

@dataclass
class A:
    a: str
    b: int

m: A

match m:
    case A(i, j):
        assert_type(i, str)
        assert_type(j, int)
                   """);
  }

  public void testMatchClassPatternCaptureDataclassNoMatchArgs() {
    doTestByText("""
from dataclasses import dataclass
from typing import assert_type

@dataclass(match_args=False)
class A:
    a: str
    b: int

m: A

match m:
    case A(i, j):
        assert_type(i, Any)
        assert_type(j, Any)
                   """);
  }

  public void testMatchClassPatternCaptureDataclassTransform() {
    doTestByText("""
from typing import dataclass_transform, Callable


@dataclass_transform()
def f[T](**kwargs) -> Callable[[type[T]], T]:
    ...

@f(match_args=True)
class C:
    foo: int
    bar: str


x = C(foo=1, bar="s")
match x:
    case C(y, z):
        assert_type(y, int)
        assert_type(z, str)
                   """);
  }
  
  // PY-81861
  public void testDataclassPartialCapture() {
    doTestByText("""
from dataclasses import dataclass
from typing import assert_type


@dataclass
class A:
    a: int
    b: str


x = A(1, "a")
match x:
    case A(y):
        assert_type(y, int)
                   """);
  }

  // PY-79832
  public void testMatchClassPatternSelfCaptureParameterized() {
    doTestByText("""
def flip(pair: list[int]):
    match pair:
        case list([x, y]):
            assert_type(x, int)
            assert_type(y, int)
        case _:
            raise TypeError("unsupported length")
                   """);
  }

  public void testTypeNarrowingUnionOfTuples() {
    doTestByText("""
from typing import assert_type


def func(val: tuple[int] | tuple[int, str]):
    match val:
        case (x,):
            assert_type(val, tuple[int])
        case (x, y):
            assert_type(val, tuple[int, str])
                   """);
  }

  public void testTypeNarrowingUnpackedTupleOfSizeOneAtTheStart() {
    doTestByText("""
from typing import assert_type


def func(val: tuple[*tuple[str], int]):
    match val:
        case (x, y):
            assert_type(val, tuple[str, int])
                   """);
  }

  public void testTypeNarrowingUnpackedTupleOfSizeTwoAtTheStart() {
    doTestByText("""
from typing import assert_type


def func(val: tuple[*tuple[str, int], int]):
    match val:
        case (x, y, z):
            assert_type(val, tuple[str, int, int])
                   """);
  }

  public void testTypeNarrowingTwoUnpackedTuples() {
    doTestByText("""
from typing import assert_type


def func(val: tuple[*tuple[str, int], str, *tuple[int, str]]):
    match val:
        case (x, y, z, a, b):
            assert_type(val, tuple[str, int, str, int, str])
                   """);
  }

  public void testTypeNarrowingUnboundUnpackedTupleAtTheStart() {
    doTestByText("""
from typing import assert_type


def func(val: tuple[*tuple[str, ...], int]):
    match val:
        case (x,):
            assert_type(val,  tuple[int])
        case (x, y):
            assert_type(val, tuple[str, int])
        case (x, y, z):
            assert_type(val, tuple[str, str, int])
                   """);
  }

  public void testTypeNarrowingUnboundUnpackedTupleInTheMiddle() {
    doTestByText("""
from typing import assert_type


def func(val: tuple[int, *tuple[str, ...], int]):
    match val:
        case (x, y):
            assert_type(val, tuple[int, int])
        case (x, y, z):
            assert_type(val, tuple[int, str, int])
        case (x, y, z, a):
            assert_type(val, tuple[int, str, str, int])
                   """);
  }

  public void testTypeNarrowingUnboundUnpackedTupleAtTheEnd() {
    doTestByText("""
from typing import assert_type


def func(val: tuple[int, int] | tuple[int, *tuple[str, ...]]):
    match val:
        case (x,):
            assert_type(val, tuple[int])
        case (x, y):
            assert_type(val, tuple[int, int] | tuple[int, str])
        case (x, y, z):
            assert_type(val, tuple[int, str, str])
                   """);
  }

  public void testTypeNarrowingUnboundUnpackedAndBoundUnpackedTuple() {
    doTestByText("""
from typing import assert_type


def func(val: tuple[*tuple[str, ...], *tuple[int, str]]):
    match val:
        case (x, y):
            assert_type(val, tuple[int, str])
        case (x, y, z):
            assert_type(val, tuple[str, int, str])
        case (x, y, z, a):
            assert_type(val, tuple[str, str, int, str])
                   """);
  }

  public void testTypeNarrowingDeepUnboundUnpackedTuple() {
    doTestByText("""
from typing import assert_type


def func(val: tuple[*tuple[str, *tuple[int, ...]]]):
    match val:
        case (x,):
            assert_type(val, tuple[str])
        case (x, y):
            assert_type(val, tuple[str, int])
        case (x, y, z):
            assert_type(val, tuple[str, int, int])
                   """);
  }

  public void testTypeNarrowingTwoUnboundUnpackedTuples() {
    doTestByText("""
from typing import assert_type


def func(val: tuple[*tuple[str, ...], *tuple[int, ...]]):
    match val:
        case (x, y):
            # actual type doesn't matter here since the original type is invalid
            # this test verifies that there are no exceptions or infinite loops
            assert_type(val, tuple[str, *tuple[int, ...]])
                   """);
  }

  // PY-53880
  public void testGuardConditionDoesNotExcludePattern() {
    doTestByText("""
from typing import assert_type


def excluding_conditional_pattern(p: int):
    match p:
        case int() if p >= 0:
            pass
        case negative:
            assert_type(negative, int)
                   """);
  }

  // PY-53880
  public void testClassPatternTypeTypeNotExhaustive() {
    doTestByText("""
from typing import assert_type, Never

class A:
    pass

def check_pattern(clazz: type[A], obj: A):
    match obj:
        case clazz(): # <-- this is not exhaustive
            assert_type(obj, A)
        case A():
            assert_type(obj, A)
        case _:
            assert_type(obj, Never)
                   """);
  }
  
  // PY-53880
  public void testListNotExhaustive() {
    doTestByText("""
from typing import assert_type


def excluding_fixed_size_list(p: list[str]):
    match p:
        case [*items, item1, item2]:
            pass
        case shorter_than_two:
            assert_type(shorter_than_two, list[str])
                   """);
  }

  // PY-79834 
  public void testMatchNamedTuplePositionalArgs() {
    doTestByText("""
from typing import NamedTuple, assert_type

class Point(NamedTuple):
    x: int
    y: str

p: Point

match p:
    case Point(x_val, y_val):
        assert_type(x_val, int)
        assert_type(y_val, str)
        assert_type(p, Point)
                   """);
  }

  // PY-79834 
  public void testMatchNamedTupleKeywordArgs() {
    doTestByText("""
from typing import NamedTuple, assert_type

class Point(NamedTuple):
    x: int
    y: str

p: Point

match p:
    case Point(x=x_val, y=y_val):
        assert_type(x_val, int)
        assert_type(y_val, str)
        assert_type(p, Point)
                   """);
  }

  // PY-79834 
  public void testMatchNamedTupleMixedArgs() {
    doTestByText("""
from typing import NamedTuple, assert_type

class Point(NamedTuple):
    x: int
    y: str
    z: bool

p: Point

match p:
    case Point(x_val, y=y_val, z=z_val):
        assert_type(x_val, int)
        assert_type(y_val, str)
        assert_type(z_val, bool)
        assert_type(p, Point)
      """);
  }

  // PY-79834 
  public void testMatchNamedTupleOutOfOrderKeywordArgs() {
    doTestByText("""
from typing import NamedTuple, assert_type

class Point(NamedTuple):
    x: int
    y: str
    z: bool

p: Point

match p:
    case Point(z=z_val, x=x_val, y=y_val):
        assert_type(x_val, int)
        assert_type(y_val, str)
        assert_type(z_val, bool)
        assert_type(p, Point)
                   """);
  }

  // PY-79834 
  public void testMatchFunctionCollectionsNamedTuple() {
    doTestByText("""
from collections import namedtuple

Point = namedtuple('Point', ['x', 'y'])

p1: Point

match p1:
    case Point(x_val, y_val):
        assert_type(x_val, Any)
        assert_type(y_val, Any)
                   """);
  }

  // PY-79834 
  public void testMatchFunctionTypingNamedTuple() {
    doTestByText("""
from typing import NamedTuple, assert_type

ColorPoint = NamedTuple('ColorPoint', [('x', int), ('y', int), ('color', str)])

p2: ColorPoint
                   
match p2:
    case ColorPoint(x_val, y_val, color_val):
        assert_type(x_val, int)
        assert_type(y_val, int)
        assert_type(color_val, str)
                   """);
  }
}