from ext import dec_no_annotation

@dec_no_annotation
def func(i: str) -> int:
  return 1

value = func(1, True)
dec_func = func