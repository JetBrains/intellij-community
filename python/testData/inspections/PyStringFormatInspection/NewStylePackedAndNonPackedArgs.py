def foo():
    return {"foo": "bar"}

print("pos: {} {} {}".format(1, *[2, 3]))

"%s" %<warning descr="Too few arguments for format string">()</warning>