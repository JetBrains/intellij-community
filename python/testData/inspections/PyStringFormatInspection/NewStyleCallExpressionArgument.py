def f(mode):
    if mode == "i":
        return 1
    elif mode == "f":
        return 1.0
    elif mode == "s":
        return ""
    elif mode == "b":
        return True

<warning descr="Too few arguments for format string">"{}{}"</warning>.format(f("i"))
<warning descr="Too few arguments for format string">"{}{}"</warning>.format(f("f"))
<warning descr="Too few arguments for format string">"{}{}"</warning>.format(f("s"))
<warning descr="Too few arguments for format string">"{}{}"</warning>.format(f("b"))