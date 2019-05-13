from typing import TypeVar

TypeVar("T")
TypeVar("T", int)
TypeVar("T", int, str)

TypeVar("T", bound=int)
TypeVar("T", int, bound=int)
TypeVar("T", int, str, bound=int)

TypeVar("T", <warning descr="Unexpected argument">bd=int</warning>)
TypeVar("T", int, <warning descr="Unexpected argument">bd=int</warning>)
TypeVar("T", int, str, <warning descr="Unexpected argument">bd=int</warning>)

TypeVar("T", bound=int, covariant=True)
TypeVar("T", int, bound=int, covariant=True)
TypeVar("T", int, str, bound=int, covariant=True)

TypeVar("T", bound=int, <warning descr="Unexpected argument">cant=True</warning>)
TypeVar("T", int, bound=int, <warning descr="Unexpected argument">cant=True</warning>)
TypeVar("T", int, str, bound=int, <warning descr="Unexpected argument">cant=True</warning>)

TypeVar("T", bound=int, covariant=True, contravariant=True)
TypeVar("T", int, bound=int, covariant=True, contravariant=True)
TypeVar("T", int, str, bound=int, covariant=True, contravariant=True)

TypeVar("T", bound=int, covariant=True, <warning descr="Unexpected argument">cant=True</warning>)
TypeVar("T", int, bound=int, covariant=True, <warning descr="Unexpected argument">cant=True</warning>)
TypeVar("T", int, str, bound=int, covariant=True, <warning descr="Unexpected argument">cant=True</warning>)

TypeVar("T", bound=int, covariant=True, contravariant=True, <warning descr="Unexpected argument">more=5</warning>)
TypeVar("T", int, bound=int, covariant=True, contravariant=True, <warning descr="Unexpected argument">more=5</warning>)
TypeVar("T", int, str, bound=int, covariant=True, contravariant=True, <warning descr="Unexpected argument">more=5</warning>)