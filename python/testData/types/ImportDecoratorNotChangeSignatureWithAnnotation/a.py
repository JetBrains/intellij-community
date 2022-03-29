from ext import dec_not_change

@dec_not_change
def func(i: str) -> int:
  return 1

value = func("")
dec_func = func