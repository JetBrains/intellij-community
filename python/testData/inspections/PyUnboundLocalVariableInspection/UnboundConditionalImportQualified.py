def f(c, x):
    if c:
        import os.path
    else:
        pass
    return <warning descr="Local variable 'os' might be referenced before assignment">os</warning>.path.isfile(x) #fail
