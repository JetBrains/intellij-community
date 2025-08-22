# Needed until mypy issues are solved or https://github.com/python/mypy/issues/12358
# pyright: reportUnnecessaryTypeIgnoreComment=false
from __future__ import annotations

from _typeshed import ReadableBuffer
from datetime import date, datetime, time
from typing import Any, List, Literal, Tuple, Union
from typing_extensions import assert_type

from openpyxl.descriptors import Strict
from openpyxl.descriptors.base import (
    Bool,
    Convertible,
    DateTime,
    Descriptor,
    Float,
    Integer,
    Length,
    MatchPattern,
    MinMax,
    NoneSet,
    Set,
    String,
    Typed,
)
from openpyxl.descriptors.serialisable import Serialisable


class WithDescriptors(Serialisable):
    descriptor = Descriptor[str]()

    typed_default = Typed(expected_type=str)
    typed_not_none = Typed(expected_type=str, allow_none=False)
    typed_none = Typed(expected_type=str, allow_none=True)

    set_tuple = Set(values=("a", 1, 0.0))
    set_list = Set(values=["a", 1, 0.0])
    set_tuple_none = Set(values=("a", 1, 0.0, None))

    noneset_tuple = NoneSet(values=("a", 1, 0.0))
    noneset_list = NoneSet(values=["a", 1, 0.0])

    length_tuple = Length[Tuple[str, str]](length=1)  # Can't validate tuple length in a generic manner
    length_list = Length[List[str]](length=1)
    length_invalid = Length[object](length=1)  # type: ignore

    match_pattern_str_default = MatchPattern(pattern="")
    match_pattern_str = MatchPattern(pattern="", allow_none=False)
    match_pattern_str_none = MatchPattern(pattern="", allow_none=True)
    match_pattern_bytes_default = MatchPattern(pattern=b"")
    match_pattern_bytes = MatchPattern(pattern=b"", allow_none=False)
    match_pattern_bytes_none = MatchPattern(pattern=b"", allow_none=True)

    convertible_default = Convertible(expected_type=int)
    convertible_not_none = Convertible(expected_type=int, allow_none=False)
    convertible_none = Convertible(expected_type=int, allow_none=True)

    # NOTE: min and max params are independent of expected_type since int and floats can always be compared together
    minmax_default = MinMax(min=0, max=0)
    minmax_float = MinMax(min=0, max=0, expected_type=float, allow_none=False)
    minmax_float_none = MinMax(min=0, max=0, expected_type=float, allow_none=True)
    minmax_int = MinMax(min=0.0, max=0.0, expected_type=int, allow_none=False)
    minmax_int_none = MinMax(min=0.0, max=0.0, expected_type=int, allow_none=True)

    bool_default = Bool()
    bool_not_none = Bool(allow_none=False)
    bool_none = Bool(allow_none=True)

    datetime_default = DateTime()
    datetime_not_none = DateTime(allow_none=False)
    datetime_none = DateTime(allow_none=True)

    string_default = String()
    string_not_none = String(allow_none=False)
    string_none = String(allow_none=True)

    float_default = Float()
    float_not_none = Float(allow_none=False)
    float_none = Float(allow_none=True)

    integer_default = Integer()
    integer_not_none = Integer(allow_none=False)
    integer_none = Integer(allow_none=True)

    # Test inferred annotation
    assert_type(descriptor, Descriptor[str])

    assert_type(typed_default, Typed[str, Literal[False]])
    assert_type(typed_not_none, Typed[str, Literal[False]])
    assert_type(typed_none, Typed[str, Literal[True]])

    assert_type(set_tuple, Set[Union[Literal["a", 1], float]])  # type: ignore[assert-type]  # False-positive in mypy
    assert_type(set_list, Set[Union[str, int, float]])  # type: ignore[assert-type]  # False-positive in mypy # Literals are simplified in non-tuples
    assert_type(set_tuple_none, Set[Union[Literal["a", 1, None], float]])  # type: ignore[assert-type]  # False-positive in mypy

    assert_type(noneset_tuple, NoneSet[Union[Literal["a", 1], float]])  # type: ignore[assert-type]  # False-positive in mypy
    assert_type(noneset_list, NoneSet[Union[str, float]])  # type: ignore[assert-type]  # False-positive in mypy# int and float are merged in generic unions

    assert_type(length_tuple, Length[Tuple[str, str]])
    assert_type(length_list, Length[List[str]])

    assert_type(match_pattern_str_default, MatchPattern[str, Literal[False]])
    assert_type(match_pattern_str, MatchPattern[str, Literal[False]])
    assert_type(match_pattern_str_none, MatchPattern[str, Literal[True]])
    assert_type(match_pattern_bytes_default, MatchPattern[ReadableBuffer, Literal[False]])
    assert_type(match_pattern_bytes, MatchPattern[ReadableBuffer, Literal[False]])
    assert_type(match_pattern_bytes_none, MatchPattern[ReadableBuffer, Literal[True]])

    assert_type(convertible_default, Convertible[int, Literal[False]])
    assert_type(convertible_not_none, Convertible[int, Literal[False]])
    assert_type(convertible_none, Convertible[int, Literal[True]])

    assert_type(minmax_default, MinMax[float, Literal[False]])
    assert_type(minmax_float, MinMax[float, Literal[False]])
    assert_type(minmax_float_none, MinMax[float, Literal[True]])
    assert_type(minmax_int, MinMax[int, Literal[False]])
    assert_type(minmax_int_none, MinMax[int, Literal[True]])

    assert_type(bool_default, Bool[Literal[False]])
    assert_type(bool_not_none, Bool[Literal[False]])
    assert_type(bool_none, Bool[Literal[True]])

    assert_type(datetime_default, DateTime[Literal[False]])
    assert_type(datetime_not_none, DateTime[Literal[False]])
    assert_type(datetime_none, DateTime[Literal[True]])

    assert_type(string_default, String[Literal[False]])
    assert_type(string_not_none, String[Literal[False]])
    assert_type(string_none, String[Literal[True]])

    assert_type(float_default, Float[Literal[False]])
    assert_type(float_not_none, Float[Literal[False]])
    assert_type(float_none, Float[Literal[True]])

    assert_type(integer_default, Integer[Literal[False]])
    assert_type(integer_not_none, Integer[Literal[False]])
    assert_type(integer_none, Integer[Literal[True]])


with_descriptors = WithDescriptors()


# Test with missing subclass
class NotSerialisable:
    descriptor = Descriptor[Any]()


NotSerialisable().descriptor = None  # type: ignore


# Test with Strict subclass
class WithDescriptorsStrict(Strict):
    descriptor = Descriptor[Any]()


WithDescriptorsStrict().descriptor = None


# Test getters
assert_type(with_descriptors.descriptor, str)

assert_type(with_descriptors.typed_not_none, str)
assert_type(with_descriptors.typed_none, Union[str, None])

assert_type(with_descriptors.set_tuple, Union[Literal["a", 1], float])  # type: ignore[assert-type]  # False-positive in mypy
assert_type(with_descriptors.set_list, Union[str, int, float])  # type: ignore[assert-type]  # False-positive in mypy  # Literals are simplified in non-tuples
assert_type(with_descriptors.set_tuple_none, Union[Literal["a", 1, None], float])  # type: ignore[assert-type]  # False-positive in mypy

assert_type(with_descriptors.noneset_tuple, Union[Literal["a", 1], float, None])  # type: ignore[assert-type]  # False-positive in mypy
assert_type(with_descriptors.noneset_list, Union[str, float, None])  # type: ignore[assert-type]  # False-positive in mypy  # int and float are merged in generic unions

assert_type(with_descriptors.length_tuple, Tuple[str, str])
assert_type(with_descriptors.length_list, List[str])

assert_type(with_descriptors.match_pattern_str, str)
assert_type(with_descriptors.match_pattern_str_none, Union[str, None])
assert_type(with_descriptors.match_pattern_bytes, ReadableBuffer)
assert_type(with_descriptors.match_pattern_bytes_none, Union[ReadableBuffer, None])

assert_type(with_descriptors.convertible_not_none, int)
assert_type(with_descriptors.convertible_none, Union[int, None])

assert_type(with_descriptors.minmax_float, float)
assert_type(with_descriptors.minmax_float_none, Union[float, None])
assert_type(with_descriptors.minmax_int, int)
assert_type(with_descriptors.minmax_int_none, Union[int, None])

assert_type(with_descriptors.bool_not_none, bool)
assert_type(with_descriptors.bool_none, Union[bool, None])

assert_type(with_descriptors.datetime_not_none, datetime)
assert_type(with_descriptors.datetime_none, Union[datetime, None])

assert_type(with_descriptors.string_not_none, str)
assert_type(with_descriptors.string_none, Union[str, None])

assert_type(with_descriptors.float_not_none, float)
assert_type(with_descriptors.float_none, Union[float, None])

assert_type(with_descriptors.integer_not_none, int)
assert_type(with_descriptors.integer_none, Union[int, None])


# Test setters (expected type, None, unexpected type)
with_descriptors.descriptor = ""
with_descriptors.descriptor = None  # type: ignore
with_descriptors.descriptor = 0  # type: ignore


with_descriptors.typed_not_none = ""
with_descriptors.typed_not_none = None  # type: ignore
with_descriptors.typed_not_none = 0  # type: ignore

with_descriptors.typed_none = ""
with_descriptors.typed_none = None
with_descriptors.typed_none = 0  # type: ignore


# NOTE: Can't check Set for literal int wen used with a float because any int is a valid float
with_descriptors.set_tuple = "a"
with_descriptors.set_tuple = 0
with_descriptors.set_tuple = 0.0
with_descriptors.set_tuple = None  # pyright: ignore[reportAttributeAccessIssue] # false negative in mypy
with_descriptors.set_tuple = "none"  # pyright: ignore[reportAttributeAccessIssue] # false negative in mypy
with_descriptors.set_tuple = object()  # pyright: ignore[reportAttributeAccessIssue] # false negative in mypy

with_descriptors.set_list = "a"
with_descriptors.set_list = 0
with_descriptors.set_list = 0.0
with_descriptors.set_list = None  # pyright: ignore[reportAttributeAccessIssue] # false negative in mypy
with_descriptors.set_list = "none"  # can't check literals validity
with_descriptors.set_list = object()  # pyright: ignore[reportAttributeAccessIssue] # false negative in mypy

with_descriptors.set_tuple_none = "a"
with_descriptors.set_tuple_none = 0
with_descriptors.set_tuple_none = 0.0
with_descriptors.set_tuple_none = None
with_descriptors.set_tuple_none = "none"  # pyright: ignore[reportAttributeAccessIssue] # false negative in mypy
with_descriptors.set_tuple_none = object()  # type: ignore


with_descriptors.noneset_tuple = "a"
with_descriptors.noneset_tuple = 0
with_descriptors.noneset_tuple = 0.0
with_descriptors.noneset_tuple = None
with_descriptors.noneset_tuple = "none"
with_descriptors.noneset_tuple = object()  # pyright: ignore[reportAttributeAccessIssue] # false negative in mypy

with_descriptors.noneset_list = "a"
with_descriptors.noneset_list = 0
with_descriptors.noneset_list = 0.0
with_descriptors.noneset_list = None
with_descriptors.noneset_list = "none"
with_descriptors.noneset_list = object()  # pyright: ignore[reportAttributeAccessIssue] # false negative in mypy


# NOTE: Can't validate tuple length in a generic manner
with_descriptors.length_tuple = ("a", "a")
with_descriptors.length_tuple = None  # type: ignore
with_descriptors.length_tuple = ["a", "a"]  # type: ignore
with_descriptors.length_tuple = ""  # type: ignore

with_descriptors.length_list = ["a", "a"]
with_descriptors.length_list = None  # type: ignore
with_descriptors.length_list = ("a", "a")  # type: ignore
with_descriptors.length_list = ""  # type: ignore


with_descriptors.match_pattern_str = ""
with_descriptors.match_pattern_str = None  # type: ignore
with_descriptors.match_pattern_str = b""  # type: ignore
with_descriptors.match_pattern_str = 0  # type: ignore

with_descriptors.match_pattern_str_none = ""
with_descriptors.match_pattern_str_none = None
with_descriptors.match_pattern_str_none = b""  # type: ignore
with_descriptors.match_pattern_str_none = 0  # type: ignore

with_descriptors.match_pattern_bytes = b""
with_descriptors.match_pattern_bytes = None  # type: ignore
with_descriptors.match_pattern_bytes = ""  # type: ignore
with_descriptors.match_pattern_bytes = 0  # type: ignore

with_descriptors.match_pattern_bytes_none = b""
with_descriptors.match_pattern_bytes_none = None
with_descriptors.match_pattern_bytes_none = ""  # type: ignore
with_descriptors.match_pattern_bytes_none = 0  # type: ignore


with_descriptors.convertible_not_none = 0
with_descriptors.convertible_not_none = "0"
with_descriptors.convertible_not_none = None  # type: ignore
with_descriptors.convertible_not_none = object()  # type: ignore

with_descriptors.convertible_none = 0
with_descriptors.convertible_none = "0"
with_descriptors.convertible_none = None
with_descriptors.convertible_none = object()  # FIXME: False negative(?) in pyright and mypy


with_descriptors.minmax_float = 0
with_descriptors.minmax_float = "0"
with_descriptors.minmax_float = 0.0
with_descriptors.minmax_float = None  # type: ignore
with_descriptors.minmax_float = object()  # type: ignore

with_descriptors.minmax_float_none = 0
with_descriptors.minmax_float_none = "0"
with_descriptors.minmax_float_none = 0.0
with_descriptors.minmax_float_none = None
with_descriptors.minmax_float_none = object()  # type: ignore

with_descriptors.minmax_int = 0
with_descriptors.minmax_int = "0"
with_descriptors.minmax_int = 0.0
with_descriptors.minmax_int = None  # type: ignore
with_descriptors.minmax_int = object()  # type: ignore

with_descriptors.minmax_int_none = 0
with_descriptors.minmax_int_none = "0"
with_descriptors.minmax_int_none = 0.0
with_descriptors.minmax_int_none = None
with_descriptors.minmax_int_none = object()  # type: ignore


with_descriptors.bool_not_none = False
with_descriptors.bool_not_none = "0"
with_descriptors.bool_not_none = 0
with_descriptors.bool_not_none = None
with_descriptors.bool_not_none = 0.0  # type: ignore
with_descriptors.bool_not_none = object()  # type: ignore

with_descriptors.bool_none = False
with_descriptors.bool_none = "0"
with_descriptors.bool_none = 0
with_descriptors.bool_none = None
with_descriptors.bool_none = 0.0  # type: ignore
with_descriptors.bool_none = object()  # type: ignore


with_descriptors.datetime_not_none = datetime(0, 0, 0)
with_descriptors.datetime_not_none = ""
with_descriptors.datetime_not_none = None  # type: ignore
with_descriptors.datetime_not_none = 0  # type: ignore
with_descriptors.datetime_not_none = date(0, 0, 0)  # type: ignore
with_descriptors.datetime_not_none = time()  # type: ignore

with_descriptors.datetime_none = datetime(0, 0, 0)
with_descriptors.datetime_none = ""
with_descriptors.datetime_none = None
with_descriptors.datetime_none = 0  # type: ignore
with_descriptors.datetime_none = date(0, 0, 0)  # type: ignore
with_descriptors.datetime_none = time()  # type: ignore


with_descriptors.string_not_none = ""
with_descriptors.string_not_none = None  # type: ignore
with_descriptors.string_not_none = 0  # type: ignore

with_descriptors.string_none = ""
with_descriptors.string_none = None
with_descriptors.string_none = 0  # type: ignore


with_descriptors.float_not_none = 0
with_descriptors.float_not_none = 0.0
with_descriptors.float_not_none = "0"
with_descriptors.float_not_none = b"0"
with_descriptors.float_not_none = None  # type: ignore
with_descriptors.float_not_none = object()  # type: ignore

with_descriptors.float_none = 0
with_descriptors.float_none = 0.0
with_descriptors.float_none = "0"
with_descriptors.float_none = b"0"
with_descriptors.float_none = None
with_descriptors.float_none = object()  # FIXME: False negative(?) in pyright and mypy


with_descriptors.integer_not_none = 0
with_descriptors.integer_not_none = 0.0
with_descriptors.integer_not_none = "0"
with_descriptors.integer_not_none = b"0"
with_descriptors.integer_not_none = None  # type: ignore
with_descriptors.integer_not_none = object()  # type: ignore

with_descriptors.integer_none = 0
with_descriptors.integer_none = 0.0
with_descriptors.integer_none = "0"
with_descriptors.integer_none = b"0"
with_descriptors.integer_none = None
with_descriptors.integer_none = object()  # FIXME: False negative(?) in pyright and mypy
