# Needed until mypy issues are solved or https://github.com/python/mypy/issues/12358
# pyright: reportUnnecessaryTypeIgnoreComment=false

# These tests are essentially a mirror of check_base_descriptors
from __future__ import annotations

from typing import Literal, Union, cast
from typing_extensions import assert_type

from openpyxl.descriptors import Strict
from openpyxl.descriptors.nested import (
    EmptyTag,
    Nested,
    NestedBool,
    NestedFloat,
    NestedInteger,
    NestedMinMax,
    NestedNoneSet,
    NestedSet,
    NestedString,
    NestedText,
    NestedValue,
)
from openpyxl.descriptors.serialisable import Serialisable
from openpyxl.xml._functions_overloads import _HasTagAndGet
from openpyxl.xml.functions import Element

_ = object()  # More concise discard object for casts

# Ensure the default "Element" matches the _HasTagAndGet protocol
element: _HasTagAndGet[str] = Element("")


class WithDescriptors(Serialisable):
    descriptor = Nested(expected_type=str)

    set_tuple = NestedSet(values=("a", 1, 0.0))
    set_list = NestedSet(values=["a", 1, 0.0])
    set_tuple_none = NestedSet(values=("a", 1, 0.0, None))

    noneset_tuple = NestedNoneSet(values=("a", 1, 0.0))
    noneset_list = NestedNoneSet(values=["a", 1, 0.0])

    convertible_default = NestedValue(expected_type=int)
    convertible_not_none = NestedValue(expected_type=int, allow_none=False)
    convertible_none = NestedValue(expected_type=int, allow_none=True)

    text_default = NestedText(expected_type=str)
    text_str_not_none = NestedText(expected_type=str, allow_none=False)
    text_str_none = NestedText(expected_type=str, allow_none=True)
    text_int_not_none = NestedText(expected_type=int, allow_none=False)
    text_int_none = NestedText(expected_type=int, allow_none=True)

    # NOTE: min and max params are independent of expected_type since int and floats can always be compared together
    minmax_default = NestedMinMax(min=0, max=0)
    minmax_float = NestedMinMax(min=0, max=0, expected_type=float, allow_none=False)
    minmax_float_none = NestedMinMax(min=0, max=0, expected_type=float, allow_none=True)
    minmax_int = NestedMinMax(min=0.0, max=0.0, expected_type=int, allow_none=False)
    minmax_int_none = NestedMinMax(min=0.0, max=0.0, expected_type=int, allow_none=True)

    bool_default = NestedBool()
    bool_not_none = NestedBool(allow_none=False)
    bool_none = NestedBool(allow_none=True)

    emptytag_default = EmptyTag()
    emptytag_not_none = EmptyTag(allow_none=False)
    emptytag_none = EmptyTag(allow_none=True)

    string_default = NestedString()
    string_not_none = NestedString(allow_none=False)
    string_none = NestedString(allow_none=True)

    float_default = NestedFloat()
    float_not_none = NestedFloat(allow_none=False)
    float_none = NestedFloat(allow_none=True)

    integer_default = NestedInteger()
    integer_not_none = NestedInteger(allow_none=False)
    integer_none = NestedInteger(allow_none=True)

    # Test inferred annotation
    assert_type(descriptor, Nested[str])

    assert_type(set_tuple, NestedSet[Union[Literal["a", 1], float]])  # type: ignore[assert-type]  # False-positive in mypy
    assert_type(set_list, NestedSet[Union[str, int, float]])  # type: ignore[assert-type]  # False-positive in mypy # Literals are simplified in non-tuples
    assert_type(set_tuple_none, NestedSet[Union[Literal["a", 1, None], float]])  # type: ignore[assert-type]  # False-positive in mypy

    assert_type(noneset_tuple, NestedNoneSet[Union[Literal["a", 1], float]])  # type: ignore[assert-type]  # False-positive in mypy
    assert_type(noneset_list, NestedNoneSet[Union[str, float]])  # type: ignore[assert-type]  # False-positive in mypy# int and float are merged in generic unions

    assert_type(convertible_default, NestedValue[int, Literal[False]])
    assert_type(convertible_not_none, NestedValue[int, Literal[False]])
    assert_type(convertible_none, NestedValue[int, Literal[True]])

    assert_type(text_default, NestedText[str, Literal[False]])
    assert_type(text_str_not_none, NestedText[str, Literal[False]])
    assert_type(text_str_none, NestedText[str, Literal[True]])
    assert_type(text_int_not_none, NestedText[int, Literal[False]])
    assert_type(text_int_none, NestedText[int, Literal[True]])

    assert_type(minmax_default, NestedMinMax[float, Literal[False]])
    assert_type(minmax_float, NestedMinMax[float, Literal[False]])
    assert_type(minmax_float_none, NestedMinMax[float, Literal[True]])
    assert_type(minmax_int, NestedMinMax[int, Literal[False]])
    assert_type(minmax_int_none, NestedMinMax[int, Literal[True]])

    assert_type(bool_default, NestedBool[Literal[False]])
    assert_type(bool_not_none, NestedBool[Literal[False]])
    assert_type(bool_none, NestedBool[Literal[True]])

    assert_type(emptytag_default, EmptyTag[Literal[False]])
    assert_type(emptytag_not_none, EmptyTag[Literal[False]])
    assert_type(emptytag_none, EmptyTag[Literal[True]])

    assert_type(string_default, NestedString[Literal[False]])
    assert_type(string_not_none, NestedString[Literal[False]])
    assert_type(string_none, NestedString[Literal[True]])

    assert_type(float_default, NestedFloat[Literal[False]])
    assert_type(float_not_none, NestedFloat[Literal[False]])
    assert_type(float_none, NestedFloat[Literal[True]])

    assert_type(integer_default, NestedInteger[Literal[False]])
    assert_type(integer_not_none, NestedInteger[Literal[False]])
    assert_type(integer_none, NestedInteger[Literal[True]])


with_descriptors = WithDescriptors()


# Test with missing subclass
class NotSerialisable:
    descriptor = Nested(expected_type=object)


NotSerialisable().descriptor = None  # type: ignore


# Test with Strict subclass
class WithDescriptorsStrict(Strict):
    descriptor = Nested(expected_type=object)


WithDescriptorsStrict().descriptor = None


# Test getters
assert_type(with_descriptors.descriptor, str)

assert_type(with_descriptors.set_tuple, Union[Literal["a", 1], float])  # type: ignore[assert-type]  # False-positive in mypy
assert_type(with_descriptors.set_list, Union[str, int, float])  # type: ignore[assert-type]  # False-positive in mypy  # Literals are simplified in non-tuples
assert_type(with_descriptors.set_tuple_none, Union[Literal["a", 1, None], float])  # type: ignore[assert-type]  # False-positive in mypy

assert_type(with_descriptors.noneset_tuple, Union[Literal["a", 1], float, None])  # type: ignore[assert-type]  # False-positive in mypy
assert_type(with_descriptors.noneset_list, Union[str, float, None])  # type: ignore[assert-type]  # False-positive in mypy  # int and float are merged in generic unions

assert_type(with_descriptors.convertible_not_none, int)
assert_type(with_descriptors.convertible_none, Union[int, None])

assert_type(with_descriptors.text_str_not_none, str)
assert_type(with_descriptors.text_str_none, Union[str, None])
assert_type(with_descriptors.text_int_not_none, int)
assert_type(with_descriptors.text_int_none, Union[int, None])

assert_type(with_descriptors.minmax_float, float)
assert_type(with_descriptors.minmax_float_none, Union[float, None])
assert_type(with_descriptors.minmax_int, int)
assert_type(with_descriptors.minmax_int_none, Union[int, None])

assert_type(with_descriptors.bool_not_none, bool)
assert_type(with_descriptors.bool_none, Union[bool, None])

assert_type(with_descriptors.emptytag_not_none, bool)
assert_type(with_descriptors.emptytag_none, Union[bool, None])

assert_type(with_descriptors.string_not_none, str)
assert_type(with_descriptors.string_none, Union[str, None])

assert_type(with_descriptors.float_not_none, float)
assert_type(with_descriptors.float_none, Union[float, None])

assert_type(with_descriptors.integer_not_none, int)
assert_type(with_descriptors.integer_none, Union[int, None])


# Test setters (expected type, None, unexpected type, Elements)
with_descriptors.descriptor = ""
with_descriptors.descriptor = None  # type: ignore
with_descriptors.descriptor = 0  # type: ignore
with_descriptors.descriptor = cast(_HasTagAndGet[str], _)
with_descriptors.descriptor = cast(_HasTagAndGet[None], _)  # type: ignore
with_descriptors.descriptor = cast(_HasTagAndGet[int], _)  # type: ignore


# NOTE: Can't check NestedSet for literal int wen used with a float because any int is a valid float
with_descriptors.set_tuple = "a"
with_descriptors.set_tuple = 0
with_descriptors.set_tuple = 0.0
with_descriptors.set_tuple = None  # pyright: ignore[reportAttributeAccessIssue] # false negative in mypy
with_descriptors.set_tuple = "none"  # pyright: ignore[reportAttributeAccessIssue] # false negative in mypy
with_descriptors.set_tuple = object()  # pyright: ignore[reportAttributeAccessIssue] # false negative in mypy
with_descriptors.set_tuple = cast(_HasTagAndGet[Literal["a"]], _)
with_descriptors.set_tuple = cast(_HasTagAndGet[str], _)  # pyright: ignore[reportAttributeAccessIssue] # false negative in mypy
with_descriptors.set_tuple = cast(_HasTagAndGet[None], _)  # pyright: ignore[reportAttributeAccessIssue] # false negative in mypy
with_descriptors.set_tuple = cast(  # pyright: ignore[reportAttributeAccessIssue] # false negative in mypy
    _HasTagAndGet[object], _
)

with_descriptors.set_list = "a"
with_descriptors.set_list = 0
with_descriptors.set_list = 0.0
with_descriptors.set_list = None  # pyright: ignore[reportAttributeAccessIssue] # false negative in mypy
with_descriptors.set_list = "none"  # can't check literals validity
with_descriptors.set_list = object()  # pyright: ignore[reportAttributeAccessIssue] # false negative in mypy
with_descriptors.set_list = cast(_HasTagAndGet[Literal["a"]], _)
with_descriptors.set_list = cast(_HasTagAndGet[str], _)  # can't check literals validity
with_descriptors.set_list = cast(_HasTagAndGet[None], _)  # pyright: ignore[reportAttributeAccessIssue] # false negative in mypy
with_descriptors.set_list = cast(_HasTagAndGet[object], _)  # pyright: ignore[reportAttributeAccessIssue] # false negative in mypy

with_descriptors.set_tuple_none = "a"
with_descriptors.set_tuple_none = 0
with_descriptors.set_tuple_none = 0.0
with_descriptors.set_tuple_none = None
with_descriptors.set_tuple_none = "none"  # pyright: ignore[reportAttributeAccessIssue] # false negative in mypy
with_descriptors.set_tuple_none = object()  # type: ignore
with_descriptors.set_tuple_none = cast(_HasTagAndGet[Literal["a"]], _)
with_descriptors.set_tuple_none = cast(  # pyright: ignore[reportAttributeAccessIssue] # false negative in mypy
    _HasTagAndGet[str], _
)
with_descriptors.set_tuple_none = cast(_HasTagAndGet[None], _)
with_descriptors.set_tuple_none = cast(_HasTagAndGet[object], _)  # type: ignore


with_descriptors.noneset_tuple = "a"
with_descriptors.noneset_tuple = 0
with_descriptors.noneset_tuple = 0.0
with_descriptors.noneset_tuple = None
with_descriptors.noneset_tuple = "none"
with_descriptors.noneset_tuple = object()  # pyright: ignore[reportAttributeAccessIssue] # false negative in mypy
with_descriptors.noneset_tuple = cast(_HasTagAndGet[Literal["a"]], _)
with_descriptors.noneset_tuple = cast(  # pyright: ignore[reportAttributeAccessIssue] # false negative in mypy
    _HasTagAndGet[str], _
)
with_descriptors.noneset_tuple = cast(_HasTagAndGet[None], _)
with_descriptors.noneset_tuple = cast(  # pyright: ignore[reportAttributeAccessIssue] # false negative in mypy
    _HasTagAndGet[object], _
)

with_descriptors.noneset_list = "a"
with_descriptors.noneset_list = 0
with_descriptors.noneset_list = 0.0
with_descriptors.noneset_list = None
with_descriptors.noneset_list = "none"
with_descriptors.noneset_list = object()  # pyright: ignore[reportAttributeAccessIssue] # false negative in mypy
with_descriptors.noneset_list = cast(_HasTagAndGet[Literal["a"]], _)
with_descriptors.noneset_list = cast(_HasTagAndGet[str], _)
with_descriptors.noneset_list = cast(_HasTagAndGet[None], _)
with_descriptors.noneset_list = cast(  # pyright: ignore[reportAttributeAccessIssue] # false negative in mypy
    _HasTagAndGet[object], _
)


with_descriptors.convertible_not_none = 0
with_descriptors.convertible_not_none = "0"
with_descriptors.convertible_not_none = None  # type: ignore
with_descriptors.convertible_not_none = object()  # type: ignore
with_descriptors.convertible_not_none = cast(_HasTagAndGet[str], _)
with_descriptors.convertible_not_none = cast(_HasTagAndGet[None], _)  # type: ignore
with_descriptors.convertible_not_none = cast(_HasTagAndGet[object], _)  # type: ignore

with_descriptors.convertible_none = 0
with_descriptors.convertible_none = "0"
with_descriptors.convertible_none = None
with_descriptors.convertible_none = object()  # FIXME: False negative(?) in pyright and mypy
with_descriptors.convertible_none = cast(_HasTagAndGet[str], _)
with_descriptors.convertible_none = cast(_HasTagAndGet[None], _)
with_descriptors.convertible_none = cast(_HasTagAndGet[object], _)  # FIXME: False negative(?) in pyright and mypy


with_descriptors.text_str_not_none = 0
with_descriptors.text_str_not_none = "0"
with_descriptors.text_str_not_none = None
with_descriptors.text_str_not_none = object()
with_descriptors.text_str_not_none = cast(_HasTagAndGet[str], _)
with_descriptors.text_str_not_none = cast(_HasTagAndGet[None], _)
with_descriptors.text_str_not_none = cast(_HasTagAndGet[object], _)

with_descriptors.text_str_none = 0
with_descriptors.text_str_none = "0"
with_descriptors.text_str_none = None
with_descriptors.text_str_none = object()
with_descriptors.text_str_none = cast(_HasTagAndGet[str], _)
with_descriptors.text_str_none = cast(_HasTagAndGet[None], _)
with_descriptors.text_str_none = cast(_HasTagAndGet[object], _)

with_descriptors.text_int_not_none = 0
with_descriptors.text_int_not_none = "0"
with_descriptors.text_int_not_none = None  # type: ignore
with_descriptors.text_int_not_none = object()  # type: ignore
# If expected type (_T) is not str, it's impossible to use an Element as the value
with_descriptors.text_int_not_none = cast(_HasTagAndGet[int], _)  # type: ignore
with_descriptors.text_int_not_none = cast(_HasTagAndGet[None], _)  # type: ignore
with_descriptors.text_int_not_none = cast(_HasTagAndGet[str], _)  # type: ignore

with_descriptors.text_int_none = 0
with_descriptors.text_int_none = "0"
with_descriptors.text_int_none = None
with_descriptors.text_int_none = object()  # FIXME: False negative(?) in pyright and mypy
# If expected type (_T) is not str, it's impossible to use an Element as the value
with_descriptors.text_int_none = cast(  # pyright: ignore[reportAttributeAccessIssue] # false negative in mypy
    _HasTagAndGet[int], _
)
with_descriptors.text_int_none = cast(  # pyright: ignore[reportAttributeAccessIssue] # false negative in mypy
    _HasTagAndGet[None], _
)
with_descriptors.text_int_none = cast(  # pyright: ignore[reportAttributeAccessIssue] # false negative in mypy
    _HasTagAndGet[str], _
)


with_descriptors.minmax_float = 0
with_descriptors.minmax_float = "0"
with_descriptors.minmax_float = 0.0
with_descriptors.minmax_float = None  # type: ignore
with_descriptors.minmax_float = object()  # type: ignore
with_descriptors.minmax_float = cast(_HasTagAndGet[float], _)
with_descriptors.minmax_float = cast(_HasTagAndGet[None], _)  # type: ignore
with_descriptors.minmax_float = cast(_HasTagAndGet[object], _)  # type: ignore

with_descriptors.minmax_float_none = 0
with_descriptors.minmax_float_none = "0"
with_descriptors.minmax_float_none = 0.0
with_descriptors.minmax_float_none = None
with_descriptors.minmax_float_none = object()  # type: ignore
with_descriptors.minmax_float_none = cast(_HasTagAndGet[float], _)
with_descriptors.minmax_float_none = cast(_HasTagAndGet[None], _)
with_descriptors.minmax_float_none = cast(_HasTagAndGet[object], _)  # type: ignore

with_descriptors.minmax_int = 0
with_descriptors.minmax_int = "0"
with_descriptors.minmax_int = 0.0
with_descriptors.minmax_int = None  # type: ignore
with_descriptors.minmax_int = object()  # type: ignore
with_descriptors.minmax_int = cast(_HasTagAndGet[int], _)
with_descriptors.minmax_int = cast(_HasTagAndGet[None], _)  # type: ignore
with_descriptors.minmax_int = cast(_HasTagAndGet[object], _)  # type: ignore

with_descriptors.minmax_int_none = 0
with_descriptors.minmax_int_none = "0"
with_descriptors.minmax_int_none = 0.0
with_descriptors.minmax_int_none = None
with_descriptors.minmax_int_none = object()  # type: ignore
with_descriptors.minmax_int_none = cast(_HasTagAndGet[int], _)
with_descriptors.minmax_int_none = cast(_HasTagAndGet[None], _)
with_descriptors.minmax_int_none = cast(_HasTagAndGet[object], _)  # type: ignore


with_descriptors.bool_not_none = False
with_descriptors.bool_not_none = "0"
with_descriptors.bool_not_none = 0
with_descriptors.bool_not_none = None
with_descriptors.bool_not_none = 0.0  # type: ignore
with_descriptors.bool_not_none = object()  # type: ignore
with_descriptors.bool_not_none = cast(_HasTagAndGet[bool], _)
with_descriptors.bool_not_none = cast(_HasTagAndGet[None], _)
with_descriptors.bool_not_none = cast(_HasTagAndGet[float], _)  # type: ignore

with_descriptors.bool_none = False
with_descriptors.bool_none = "0"
with_descriptors.bool_none = 0
with_descriptors.bool_none = None
with_descriptors.bool_none = 0.0  # type: ignore
with_descriptors.bool_none = object()  # type: ignore
with_descriptors.bool_none = cast(_HasTagAndGet[bool], _)
with_descriptors.bool_none = cast(_HasTagAndGet[None], _)
with_descriptors.bool_none = cast(_HasTagAndGet[float], _)  # type: ignore


with_descriptors.emptytag_not_none = False
with_descriptors.emptytag_not_none = "0"
with_descriptors.emptytag_not_none = 0
with_descriptors.emptytag_not_none = None
with_descriptors.emptytag_not_none = 0.0  # type: ignore
with_descriptors.emptytag_not_none = object()  # type: ignore
with_descriptors.emptytag_not_none = cast(_HasTagAndGet[bool], _)
with_descriptors.emptytag_not_none = cast(_HasTagAndGet[None], _)
with_descriptors.emptytag_not_none = cast(_HasTagAndGet[float], _)  # type: ignore

with_descriptors.emptytag_none = False
with_descriptors.emptytag_none = "0"
with_descriptors.emptytag_none = 0
with_descriptors.emptytag_none = None
with_descriptors.emptytag_none = 0.0  # type: ignore
with_descriptors.emptytag_none = object()  # type: ignore
with_descriptors.emptytag_none = cast(_HasTagAndGet[bool], _)
with_descriptors.emptytag_none = cast(_HasTagAndGet[None], _)
with_descriptors.emptytag_none = cast(_HasTagAndGet[float], _)  # type: ignore


with_descriptors.string_not_none = ""
with_descriptors.string_not_none = None
with_descriptors.string_not_none = 0
with_descriptors.string_not_none = object()
with_descriptors.string_not_none = cast(_HasTagAndGet[str], _)
with_descriptors.string_not_none = cast(_HasTagAndGet[None], _)
with_descriptors.string_not_none = cast(_HasTagAndGet[int], _)
with_descriptors.string_not_none = cast(_HasTagAndGet[object], _)

with_descriptors.string_none = ""
with_descriptors.string_none = None
with_descriptors.string_none = 0
with_descriptors.string_none = object()
with_descriptors.string_none = cast(_HasTagAndGet[str], _)
with_descriptors.string_none = cast(_HasTagAndGet[None], _)
with_descriptors.string_none = cast(_HasTagAndGet[int], _)
with_descriptors.string_none = cast(_HasTagAndGet[object], _)


with_descriptors.float_not_none = 0
with_descriptors.float_not_none = 0.0
with_descriptors.float_not_none = "0"
with_descriptors.float_not_none = b"0"
with_descriptors.float_not_none = None  # type: ignore
with_descriptors.float_not_none = object()  # type: ignore
with_descriptors.float_not_none = cast(_HasTagAndGet[float], _)
with_descriptors.float_not_none = cast(_HasTagAndGet[None], _)  # type: ignore
with_descriptors.float_not_none = cast(_HasTagAndGet[object], _)  # type: ignore

with_descriptors.float_none = 0
with_descriptors.float_none = 0.0
with_descriptors.float_none = "0"
with_descriptors.float_none = b"0"
with_descriptors.float_none = None
with_descriptors.float_none = object()  # FIXME: False negative(?) in pyright and mypy
with_descriptors.float_none = cast(_HasTagAndGet[float], _)
with_descriptors.float_none = cast(_HasTagAndGet[None], _)
with_descriptors.float_none = cast(_HasTagAndGet[object], _)  # FIXME: False negative(?) in pyright and mypy


with_descriptors.integer_not_none = 0
with_descriptors.integer_not_none = 0.0
with_descriptors.integer_not_none = "0"
with_descriptors.integer_not_none = b"0"
with_descriptors.integer_not_none = None  # type: ignore
with_descriptors.integer_not_none = object()  # type: ignore
with_descriptors.integer_not_none = cast(_HasTagAndGet[int], _)
with_descriptors.integer_not_none = cast(_HasTagAndGet[None], _)  # type: ignore
with_descriptors.integer_not_none = cast(_HasTagAndGet[object], _)  # type: ignore

with_descriptors.integer_none = 0
with_descriptors.integer_none = 0.0
with_descriptors.integer_none = "0"
with_descriptors.integer_none = b"0"
with_descriptors.integer_none = None
with_descriptors.integer_none = object()  # FIXME: False negative(?) in pyright and mypy
with_descriptors.integer_none = cast(_HasTagAndGet[int], _)
with_descriptors.integer_none = cast(_HasTagAndGet[None], _)
with_descriptors.integer_none = cast(_HasTagAndGet[object], _)  # FIXME: False negative(?) in pyright and mypy
