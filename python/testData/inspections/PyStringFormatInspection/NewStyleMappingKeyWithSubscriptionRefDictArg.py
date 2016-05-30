d = {"a": 1}
"{foo[a]}".format(foo=d)
"{foo[b]}".format(foo=<warning descr="Too few mapping keys">d</warning>)

d_num = {1: 1}
"{foo[1]}".format(foo=d_num)
"{foo[2]}".format(foo=<warning descr="Too few mapping keys">d_num</warning>)