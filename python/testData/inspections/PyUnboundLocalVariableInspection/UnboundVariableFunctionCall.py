def f(g, h, x):
    if x > 1:
        z = g
    elif x:
        z = h
        # local z may be unbound, inspection fails only when z is in function call
    return <warning descr="Local variable 'z' might be referenced before assignment">z</warning>() #fail
