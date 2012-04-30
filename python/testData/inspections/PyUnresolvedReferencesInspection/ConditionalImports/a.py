def f(c, x):
    if c > 2:
        from lib1 import f #pass
    elif c > 1:
        from lib2 import f #pass
    else:
        <warning descr="Unused import statement">from lib2 import g #fail</warning>
    return f(x)