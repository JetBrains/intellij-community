"""
Test for type expressions used in annotations.
"""

import abc
import abc
import types
import types
from typing import Any, Callable, Tuple, Union, assert_type

# https://typing.readthedocs.io/en/latest/spec/annotations.html#valid-type-expression-forms


def greeting(name: str) -> str:
    return "Hello " + name


assert_type(greeting("Monty"), str)


# > Expressions whose type is a subtype of a specific argument type are also accepted for that argument.
class StrSub(str):
    ...


assert_type(greeting(StrSub("Monty")), str)


# > Type hints may be built-in classes (including those defined in standard library or third-party
# > extension modules), abstract base classes, types available in the types module, and user-defined
# > classes (including those defined in the standard library or third-party modules).


class UserDefinedClass:
    ...


class AbstractBaseClass(abc.ABC):
    @abc.abstractmethod
    def abstract_method(self):
        ...


# The following parameter annotations should all be considered
# valid and not generate errors.
def valid_annotations(
    p1: int,
    p2: str,
    p3: bytes,
    p4: bytearray,
    p5: memoryview,
    p6: complex,
    p7: float,
    p8: bool,
    p9: object,
    p10: type,
    p11: types.ModuleType,
    p12: types.FunctionType,
    p13: types.BuiltinFunctionType,
    p14: UserDefinedClass,
    p15: AbstractBaseClass,
    p16: int,
    p17: Union[int, str],
    p18: None,
    p19: list,
    p20: list[int],
    p21: tuple,
    p22: Tuple[int, ...],
    p23: Tuple[int, int, str],
    p24: Callable[..., int],
    p25: Callable[[int, str], None],
    p26: Any,
):
    assert_type(p17, int | str)
    assert_type(p19, list[Any])
    assert_type(p20, list[int])
    assert_type(p21, tuple[Any, ...])


# > Annotations should be kept simple or static analysis tools may not be able to interpret the values.

var1 = 3


# The following parameter annotations should all be considered
# invalid and generate errors.
def invalid_annotations(
    p1: eval("".join(map(chr, [105, 110, 116]))),  # E
    p2: [int, str],  # E
    p3: (int, str),  # E
    p4: [int for i in range(1)],  # E
    p5: {},  # E
    p6: (lambda: int)(),  # E
    p7: [int][0],  # E
    p8: int if 1 < 3 else str,  # E
    p9: var1,  # E
    p10: True,  # E
    p11: 1,  # E
    p12: -1,  # E
    p13: int or str,  # E
    p14: f"int",  # E
    p15: types,  # E
):
    pass


# > When used in a type hint, the expression None is considered equivalent to type(None).


def takes_None(x: None) -> None:
    ...


assert_type(takes_None(None), None)
