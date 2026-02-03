def f(c, x):
    if c:
        from re import compile as g
    else:
        pass
    return <warning descr="Local variable 'g' might be referenced before assignment">g</warning>(x) #fail
