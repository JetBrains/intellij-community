from ext import first_dec_same, second_dec_same


@second_dec_same
@first_dec_same
def func(i: str) -> int:
  return 1


value = func("")
dec_func = func