def f(a, L=<warning descr="Default argument value is mutable">[]</warning>):
    L.append(a)
    return L

def f(a, L=<warning descr="Default argument value is mutable">list()</warning>):
    L.append(a)
    return L


def f(a, L=<warning descr="Default argument value is mutable">set()</warning>):
    L.append(a)
    return L

def f(a, L=<warning descr="Default argument value is mutable">{}</warning>):
    L.append(a)
    return L


def f(a, L=<warning descr="Default argument value is mutable">dict()</warning>):
    L.append(a)
    return L

def f(a, L=<warning descr="Default argument value is mutable">{1: 2}</warning>):
    L.append(a)
    return L
