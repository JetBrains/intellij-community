from ext import prepend_bool, prepend_int, prepend_str

@prepend_str
@prepend_int
@prepend_bool
def f():
    pass

value = f('foo', 42, True)
dec_func = f