def f(c):
    if c:
        x = 1
    <warning descr="Local variable 'x' might be referenced before assignment">x</warning> += 1 #fail
    return x
