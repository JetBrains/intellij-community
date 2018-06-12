from typing import TypeVar

TypeVar("T", int, str, bound=int, covariant=True, contravariant=True)
TypeVar("T", <warning descr="Expected type 'type', got 'int' instead">0</warning>, <warning descr="Expected type 'type', got 'int' instead">1</warning>, <warning descr="Expected type 'type', got 'int' instead">bound=2</warning>, <warning descr="Expected type 'bool', got 'int' instead">covariant=3</warning>, <warning descr="Expected type 'bool', got 'int' instead">contravariant=4</warning>)