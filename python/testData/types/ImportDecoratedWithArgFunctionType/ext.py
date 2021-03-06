from typing import Callable

def dec_with_arg(i: int) -> Callable[ [Callable[[str], int]], Callable[[int], str] ]:
  def dec_inside(fun):
    def wrapper():
      return 1
    return wrapper
  return dec_inside

@dec_with_arg(1)
def func(i: str) -> int:
  return 1