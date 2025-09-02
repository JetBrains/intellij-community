from typing import Any, Callable, overload

@overload
def my_decorator(fn: Callable[[Any], int]) -> Callable[[str], str]:
  ...

@overload
def my_decorator(fn: Callable[[Any], int]) -> Callable[[bool], bool]:
  ...

def my_decorator(fn: Callable[[Any], int]) :
  pass