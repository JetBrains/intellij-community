# Specification: https://typing.readthedocs.io/en/latest/spec/generics.html#instantiating-generic-classes-and-type-erasure

from typing import Any, TypeVar, Generic, assert_type

T = TypeVar("T")

# > If the constructor (__init__ or __new__) uses T in its signature, and a
# > corresponding argument value is passed, the type of the corresponding
# > argument(s) is substituted. Otherwise, Any is assumed.

class Node(Generic[T]):
    label: T
    def __init__(self, label: T | None = None) -> None:
        if label is not None:
            self.label = label

assert_type(Node(''), Node[str])
assert_type(Node(0), Node[int])
assert_type(Node(), Node[Any])

assert_type(Node(0).label, int)
assert_type(Node().label, Any)

# > In case the inferred type uses [Any] but the intended type is more specific,
# > you can use an annotation to force the type of the variable, e.g.:

n1: Node[int] = Node()
assert_type(n1, Node[int])
n2: Node[str] = Node()
assert_type(n2, Node[str])

n3 = Node[int]()
assert_type(n3, Node[int])
n4 = Node[str]()
assert_type(n4, Node[str])

n5 = Node[int](0)   # OK
n6 = Node[int]("")  # E
n7 = Node[str]("")  # OK
n8 = Node[str](0)   # E

Node[int].label = 1  # E
Node[int].label      # E
Node.label = 1       # E
Node.label           # E
type(n1).label       # E
assert_type(n1.label, int)
assert_type(Node[int]().label, int)
n1.label = 1         # OK

# > [...] generic versions of concrete collections can be instantiated:

from typing import DefaultDict

data = DefaultDict[int, bytes]()
assert_type(data[0], bytes)
