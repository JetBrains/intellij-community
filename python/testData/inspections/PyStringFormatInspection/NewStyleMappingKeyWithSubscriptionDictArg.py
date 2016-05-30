"{foo[a]}".format(foo={"a": 1})
"{foo[b]}".format(foo=<warning descr="Too few mapping keys">{"a": 1}</warning>)

"{foo[1]}".format(foo={1: 1})
"{foo[2]}".format(foo=<warning descr="Too few mapping keys">{1: 1}</warning>)