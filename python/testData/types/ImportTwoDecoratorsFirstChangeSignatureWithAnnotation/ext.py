from typing import Callable

def first_dec_change(fun) -> Callable[[int, bool], str]:
  def wrapper(i: int, b: bool):
    return ""
  return wrapper


def second_dec_same(fun):
  return fun
