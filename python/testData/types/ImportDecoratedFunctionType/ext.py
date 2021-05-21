from typing import Callable, TypeVar

T = TypeVar("T")

def dec(fun: T) -> Callable[[str], int]:
  def wrapper():
    return 1
  return wrapper

@dec
def func(i: int) -> str:
  return "sd"