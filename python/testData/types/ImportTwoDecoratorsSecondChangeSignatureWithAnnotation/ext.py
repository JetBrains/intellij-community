from typing import Callable

def second_dec_change(fun) -> Callable[[int, bool], str]:
  def wrapper(i: int, b: bool):
    return ""
  return wrapper


def first_dec_same(fun):
  return fun
