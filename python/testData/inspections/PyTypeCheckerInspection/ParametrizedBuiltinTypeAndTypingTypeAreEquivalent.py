from typing import Type, TypeVar


def expects_typing_Type(x: Type[str]):
    expects_builtin_type(x)
    expects_builtin_type(<warning descr="Expected type 'type[str]', got 'type[int]' instead">int</warning>)


def expects_builtin_type(x: type[str]):
    expects_typing_Type(x)
    expects_typing_Type(<warning descr="Expected type 'type[str]', got 'type[int]' instead">int</warning>)


T = TypeVar('T', bound=str)


def expects_generic_builtin_type(x: type[T]):
    expects_generic_typing_Type(x)
    expects_generic_typing_Type(<warning descr="Expected type 'type[T ≤: str]', got 'type[int]' instead">int</warning>)


def expects_generic_typing_Type(x: Type[T]):
    expects_generic_builtin_type(x)
    expects_generic_builtin_type(<warning descr="Expected type 'type[T ≤: str]', got 'type[int]' instead">int</warning>)
