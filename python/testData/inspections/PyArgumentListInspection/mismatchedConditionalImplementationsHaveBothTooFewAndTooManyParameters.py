if undefined:
    def f(x):
        pass
else:
    def f(x, y, z):
        pass

f<warning descr="No overload of 'f' matches the arguments. Argument types: (int, int). Expected one of: (x), (x, y, z)">(1, 2)</warning>