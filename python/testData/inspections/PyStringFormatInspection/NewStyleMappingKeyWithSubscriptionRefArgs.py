list = [1, 2, 3]
"{foo[1]}".format(foo=list)
<warning descr="Too few arguments for format string">"{foo[3]}"</warning>.format(foo=list)

tuple = (1, 2, 3)
"{foo[1]}".format(foo=tuple)
<warning descr="Too few arguments for format string">"{foo[3]}"</warning>.format(foo=tuple)