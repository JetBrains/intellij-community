"{foo[1]}".format(foo=(1, 2, 3))
"{foo[1]}".format(foo=({1: 1}))
"{foo[1]}".format(foo=([1, 2, 3]))

"{foo[a]}".format(foo=({"a": 1}))

"{foo[3]}".format(foo=<warning descr="Too few arguments for format string">(1, 2, 3)</warning>)
"{foo[3]}".format(foo=(<warning descr="Too few mapping keys">{1: 1}</warning>))
"{foo[3]}".format(foo=<warning descr="Too few arguments for format string">([1, 2, 3])</warning>)
"{foo[b]}".format(foo=(<warning descr="Too few mapping keys">{"a": 1}</warning>))