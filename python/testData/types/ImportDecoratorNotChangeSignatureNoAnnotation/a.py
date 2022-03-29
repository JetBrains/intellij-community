from ext import dec_not_change

@dec_not_change
def func(name_in_func: str) -> int:
  return 1

value = func("")
dec_func = func