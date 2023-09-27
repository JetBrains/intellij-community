from typing import TypeVar

TypeVar("T", int, str, bound=int, covariant=True, contravariant=True)
TypeVar("T", int, str, bound='int', covariant=True, contravariant=True)
TypeVar("T", int, 'str', bound=int, covariant=True, contravariant=True)
TypeVar("T", 'int', 'str', bound=int, covariant=True, contravariant=True)
TypeVar<warning descr="Unexpected type(s):(LiteralString, int, int, int, int, int)Possible type(s):(str, Any | None, bool, bool, Any, Any)(str, Any | None, bool, bool, Any, Any)" textAttributesKey="WARNING_ATTRIBUTES">("T", 0, 1, bound=2, covariant=3, contravariant=4)</warning>