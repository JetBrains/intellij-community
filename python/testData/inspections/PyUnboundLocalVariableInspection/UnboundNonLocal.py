def f1():
    nonlocal <warning descr="Nonlocal variable 'x' must be bound in an outer function scope">x</warning> #fail


def f2():
    def g():
        nonlocal <warning descr="Nonlocal variable 'x' must be bound in an outer function scope">x</warning> #fail
        print(x)


x = 1

def f3():
    nonlocal <warning descr="Nonlocal variable 'x' must be bound in an outer function scope">x</warning> #fail
    x = 2


def f4():
    x = 0
    def g():
        nonlocal x #pass
        x = 2
        return x
    return g()
