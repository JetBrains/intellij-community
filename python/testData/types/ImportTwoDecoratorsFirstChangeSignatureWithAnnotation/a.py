from ext import first_dec_change, second_dec_same


@second_dec_same
@first_dec_change
def func(i: str) -> int:
  return 1


value = func(1, True)
dec_func = func