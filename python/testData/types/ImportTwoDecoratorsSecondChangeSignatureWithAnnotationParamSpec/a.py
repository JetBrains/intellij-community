from ext import first_dec_same, second_dec_change


@second_dec_change
@first_dec_same
def func(i: str) -> int:
  return 1


value = func(42, "")
dec_func = func