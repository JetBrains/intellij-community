list = [1, 2, 3]
"{foo[1]}".format(foo=list)
"{foo[3]}".format(foo=<warning descr="Too few arguments for format string">list</warning>)

tuple = (1, 2, 3)
"{foo[1]}".format(foo=tuple)
"{foo[3]}".format(foo=<warning descr="Too few arguments for format string">tuple</warning>)