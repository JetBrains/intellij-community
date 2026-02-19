"""
Tests the handling of builtins.type[T].
"""

# Specification: https://typing.readthedocs.io/en/latest/spec/special-types.html#type


from typing import Any, Callable, Generic, Type, TypeAlias, TypeVar, assert_type


class User:
    ...


class BasicUser(User):
    ...


class ProUser(User):
    ...


class TeamUser(User):
    ...


def func1(user_class: type) -> User:
    return user_class()


def func2(user_class: type[User]) -> User:
    return user_class()


U = TypeVar("U", bound=User)


def func3(user_class: type[U]) -> U:
    return user_class()


assert_type(func1(TeamUser), User)
assert_type(func2(TeamUser), User)
assert_type(func3(TeamUser), TeamUser)


# > Note that it is legal to use a union of classes as the parameter for type[].


def func4(user_class: type[BasicUser | ProUser]) -> User:
    return user_class()


assert_type(func4(BasicUser), User)
assert_type(func4(ProUser), User)
func4(TeamUser)  # E


# > Any other special constructs like Callable are not allowed as an argument to type.

T = TypeVar("T")


def func5(x: type[T]) -> None:
    pass


func5(User)  # OK
func5(tuple)  # OK
func5(Callable)  # E


# > When type is parameterized it requires exactly one parameter. Plain type
# > without brackets, the root of Python’s metaclass hierarchy, is equivalent to type[Any].

bad_type1: type[int, str]  # E


class MyMetaA(type):
    ...


def func6(a: type, b: type[Any]):
    assert_type(a, type[Any])
    a = b  # OK
    b = a  # OK
    a = type  # OK
    b = type  # OK


# > Regarding the behavior of type[Any] (or type), accessing attributes of a
# > variable with this type only provides attributes and methods defined by
# > type (for example, __repr__() and __mro__). Such a variable can be called
# > with arbitrary arguments, and the return type is Any.


def func7(a: type, b: type[Any], c: Type, d: Type[Any]):
    assert_type(a.__mro__, tuple[type, ...])
    assert_type(a.unknown, Any)
    assert_type(a.unknown(), Any)

    assert_type(b.__mro__, tuple[type, ...])
    assert_type(b.unknown, Any)
    assert_type(b.unknown(), Any)

    assert_type(c.__mro__, tuple[type, ...])
    assert_type(c.unknown, Any)
    assert_type(c.unknown(), Any)

    assert_type(d.__mro__, tuple[type, ...])
    assert_type(d.unknown, Any)
    assert_type(d.unknown(), Any)


def func8(a: type[object], b: Type[object]):
    assert_type(a.__name__, str)
    a.unknown  # E

    assert_type(b.__name__, str)
    b.unknown  # E


# > type is covariant in its parameter, because type[Derived] is a subtype of type[Base]:


def func9(pro_user_class: type[ProUser]):
    assert_type(func3(pro_user_class), ProUser)


TA1: TypeAlias = Type
TA2: TypeAlias = Type[Any]
TA3: TypeAlias = type
TA4: TypeAlias = type[Any]


def func10(a: TA1, b: TA2, c: TA3, d: TA4):
    assert_type(a, type[Any])
    assert_type(b, type[Any])
    assert_type(c, type[Any])
    assert_type(d, type[Any])


TA1.unknown  # E
TA2.unknown  # E
TA3.unknown  # E
TA4.unknown  # E

TA7: TypeAlias = type[T]
TA8: TypeAlias = Type[T]


def func11(t1: TA7[T]) -> T:
    return t1()


def func12(t1: TA8[T]) -> T:
    return t1()


assert_type(func11(int), int)
assert_type(func12(int), int)


def func13(v: type):
    x1: Callable[..., Any] = v  # OK
    x2: Callable[[int, int], int] = v  # OK
    x3: object = v  # OK
    x4: type = v  # OK
    x5: type[int] = v  # OK
    x6: type[Any] = v  # OK


class ClassA(Generic[T]):
    def method1(self, v: type) -> type[T]:
        return v  # OK
