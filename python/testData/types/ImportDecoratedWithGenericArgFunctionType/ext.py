from typing import Callable
from typing import TypeVar

T = TypeVar('T')

def dec_with_arg(i: T) -> Callable[ [Callable[[str], int]], Callable[[T], str] ]:
  def dec_inside(fun):
    def wrapper(ii: T) -> str:
      return str(1)
    return wrapper
  return dec_inside

@dec_with_arg(1)
def func(i: str) -> int:
  return 1