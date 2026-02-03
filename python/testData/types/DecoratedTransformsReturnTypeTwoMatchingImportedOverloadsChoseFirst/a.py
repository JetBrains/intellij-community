from ext import my_decorator

@my_decorator
def f(a: Any) -> int:
  return 3


value = f()
dec_func = f