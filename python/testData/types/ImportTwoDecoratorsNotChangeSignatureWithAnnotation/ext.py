from typing import Callable

def first_dec_same(fun) -> Callable[[str], int]:
  return fun


def second_dec_same(fun) -> Callable[[str], int]:
  return fun
