from typing import Callable

def dec_no_annotation(fun):
  def wrapper(i: int, b: bool):
    return ""
  return wrapper