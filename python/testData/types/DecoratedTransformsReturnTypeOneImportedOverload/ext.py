from typing import Callable, overload


@overload
def my_decorator(fn: Callable[[], int]) -> Callable[[], str]:
  ...

def my_decorator(fn: Callable[[], int]) :
  pass