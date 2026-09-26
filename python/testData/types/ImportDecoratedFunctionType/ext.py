from typing import Callable, TypeVar, ParamSpec

T = TypeVar("T")
P = ParamSpec("P")

def dec(fun: Callable[P, T]) -> Callable[P, int]:
  def wrapper():
    return 1
  return wrapper

@dec
def func(i: int) -> str:
  return "sd"