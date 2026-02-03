if undefined:
    def f(x):
        pass
else:
    def f(x, y, z):
        pass

f<warning descr="Incorrect argument(s)Possible callees:f(x)f(x, y, z)">(1, 2)</warning>