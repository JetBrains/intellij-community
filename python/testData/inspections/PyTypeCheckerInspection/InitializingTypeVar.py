from typing import TypeVar

TypeVar("T", int, str, bound=int, covariant=True, contravariant=True)
TypeVar("T", int, str, bound='int', covariant=True, contravariant=True)
TypeVar("T", int, 'str', bound=int, covariant=True, contravariant=True)
TypeVar("T", 'int', 'str', bound=int, covariant=True, contravariant=True)
TypeVar("T", 0, 1, bound=2, <warning descr="Expected type 'bool', got 'int' instead">covariant=3</warning>, <warning descr="Expected type 'bool', got 'int' instead">contravariant=4</warning>)