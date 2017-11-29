"{foo[1]}".format(foo=(1, 2, 3))
"{foo[1]}".format(foo=({1: 1}))
"{foo[1]}".format(foo=([1, 2, 3]))

"{foo[a]}".format(foo=({"a": 1}))

<warning descr="Too few arguments for format string">"{foo[3]}"</warning>.format(foo=(1, 2, 3))
<warning descr="Too few arguments for format string">"{foo[3]}"</warning>.format(foo=({1: 1}))
<warning descr="Too few arguments for format string">"{foo[3]}"</warning>.format(foo=([1, 2, 3]))
<warning descr="Too few arguments for format string">"{foo[b]}"</warning>.format(foo=({"a": 1}))