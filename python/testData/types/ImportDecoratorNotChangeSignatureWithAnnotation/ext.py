from typing import Callable

def dec_not_change(fun) -> Callable[[str], int]:
  def wrapper(s: str):
    return 42
  return wrapper