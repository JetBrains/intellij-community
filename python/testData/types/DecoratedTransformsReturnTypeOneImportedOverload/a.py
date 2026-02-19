from ext import my_decorator

@my_decorator
def f() -> int:
  pass


value = f()
dec_func = f