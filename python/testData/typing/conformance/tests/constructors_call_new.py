"""
Tests the evaluation of calls to constructors when there is a __new__
method defined.
"""

# Specification: https://typing.readthedocs.io/en/latest/spec/constructors.html#new-method


from typing import Any, Generic, NoReturn, Self, TypeVar, assert_type

T = TypeVar("T")


class Class1(Generic[T]):
    def __new__(cls, x: T) -> Self:
        return super().__new__(cls)


assert_type(Class1[int](1), Class1[int])
assert_type(Class1[float](1), Class1[float])
Class1[int](1.0)  # E

assert_type(Class1(1), Class1[int])
assert_type(Class1(1.0), Class1[float])


class Class2(Generic[T]):
    def __new__(cls, *args, **kwargs) -> Self:
        return super().__new__(cls)

    def __init__(self, x: T) -> None:
        pass


assert_type(Class2(1), Class2[int])
assert_type(Class2(""), Class2[str])


class Class3:
    def __new__(cls) -> int:
        return 0

    # In this case, the __init__ method should not be considered
    # by the type checker when evaluating a constructor call.
    def __init__(self, x: int):
        pass


assert_type(Class3(), int)

# > For purposes of this test, an explicit return type of Any (or a union
# > containing Any) should be treated as a type that is not an instance of
# > the class being constructed.


class Class4:
    def __new__(cls) -> "Class4 | Any":
        return 0

    def __init__(self, x: int):
        pass


assert_type(Class4(), Class4 | Any)


class Class5:
    def __new__(cls) -> NoReturn:
        raise NotImplementedError

    def __init__(self, x: int):
        pass


try:
    assert_type(Class5(), NoReturn)
except:
    pass


class Class6:
    def __new__(cls) -> "int | Class6":
        return 0

    def __init__(self, x: int):
        pass


assert_type(Class6(), int | Class6)

# > If the return type of __new__ is not annotated, a type checker may assume
# > that the return type is Self and proceed with the assumption that the
# > __init__ method will be called.


class Class7:
    def __new__(cls, *args, **kwargs):
        return super().__new__(cls, *args, **kwargs)

    def __init__(self, x: int):
        pass


assert_type(Class7(1), Class7)


# > If the class is generic, it is possible for a __new__ method to override
# > the specialized class type and return a class instance that is specialized
# > with different type arguments.


class Class8(Generic[T]):
    def __new__(cls, *args, **kwargs) -> "Class8[list[T]]": ...


assert_type(Class8[int](), Class8[list[int]])
assert_type(Class8[str](), Class8[list[str]])


# > If the cls parameter within the __new__ method is not annotated,
# > type checkers should infer a type of type[Self].


class Class9(Generic[T]):
    def __new__(cls, *args, **kwargs) -> Self: ...


class Class10(Class9[int]):
    pass


c10: Class9[int] = Class10()

# > Regardless of whether the type of the cls parameter is explicit or
# > inferred, the type checker should bind the class being constructed to
# > the cls parameter and report any type errors that arise during binding.


class Class11(Generic[T]):
    def __new__(cls: "type[Class11[int]]") -> "Class11[int]": ...


Class11()  # OK
Class11[int]()  # OK
Class11[str]()  # E
