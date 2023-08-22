from typing import TypeVar, Generic, Callable, ParamSpec, Concatenate

U = TypeVar("U")
P = ParamSpec("P")


class Y(Generic[U, P]):
   f: Callable[Concatenate[int, P], U]
   attr: U

   def __i<the_ref>nit__(self, f: Callable[Concatenate[int, P], U], attr: U) -> None:
       self.f = f
       self.attr = attr


def a(q: int, s: str, b: bool) -> str: ...


expr = Y(a, '1').f(42, "42" , True)