def d():
    return {"a": 1}

"{foo[a]}".format(foo=d())
"{foo[b]}".format(foo=<warning descr="Too few mapping keys">d()</warning>)


def d_dict():
    return dict(a=1)

"{foo[a]}".format(foo=d_dict())
"{foo[b]}".format(foo=<warning descr="Too few mapping keys">d_dict()</warning>)