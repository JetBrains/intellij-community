from typing import Callable

def dec_not_change(fun):
  def wrapper(name_in_wrapper: str):
    return 42
  return wrapper