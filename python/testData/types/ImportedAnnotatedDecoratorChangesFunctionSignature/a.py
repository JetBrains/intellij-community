from ext import dec_annotation

@dec_annotation
def func(i: str) -> int:
  return 1

value = func(1, True)
dec_func = func