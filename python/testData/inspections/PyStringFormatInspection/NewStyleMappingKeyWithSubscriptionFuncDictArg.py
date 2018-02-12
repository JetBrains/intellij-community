def d():
    return {"a": 1}

"{foo[a]}".format(foo=d())
<warning descr="Too few arguments for format string">"{foo[b]}"</warning>.format(foo=d())


def d_dict():
    return dict(a=1)

"{foo[a]}".format(foo=d_dict())
<warning descr="Too few arguments for format string">"{foo[b]}"</warning>.format(foo=d_dict())