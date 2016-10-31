d = {"a": 1}
"{foo[a]}".format(foo=d)
<warning descr="Too few arguments for format string">"{foo[b]}"</warning>.format(foo=d)

d_num = {1: 1}
"{foo[1]}".format(foo=d_num)
<warning descr="Too few arguments for format string">"{foo[2]}"</warning>.format(foo=d_num)