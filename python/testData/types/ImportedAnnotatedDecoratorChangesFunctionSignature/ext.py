from typing import Callable

def dec_annotation(fun) -> Callable[[int, bool], str]:
  def wrapper(i: int, b: bool):
    return ""
  return wrapper