def f(c):
    if c:
        import sys
    return <warning descr="Local variable 'sys' might be referenced before assignment">sys</warning>
