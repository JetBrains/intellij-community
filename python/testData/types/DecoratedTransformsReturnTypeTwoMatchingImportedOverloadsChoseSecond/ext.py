from typing import Any, Callable, overload

@overload
def my_decorator(fn: Callable[[int], int]) -> Callable[[str], str]:
  ...

@overload
def my_decorator(fn: Callable[[str], int]) -> Callable[[bool], bool]:
  ...

def my_decorator(fn: Callable[[Any], int]) :
  pass