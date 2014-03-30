def d1(f):
    return f

def d2(f):
    return 0

d3 = lambda f: f

d4 = lambda f: 0

d5 = 1

def d6():
    def d(f):
        return f
    return d

def d7():
    return lambda f: f

def d8(msg):
    def d(f):
        def wrapper(*args, **kwargs):
            print(msg)
            return f(*args, **kwargs)
        return wrapper
    return d

@d1
def f():
    pass

@d1()
def f():
    pass

@d2
def f():
    pass

<warning descr="'int' object is not callable">@d2()</warning>
def f():
    pass

@d3
def f():
    pass

@d3()
def f():
    pass

@d4
def f():
    pass

<warning descr="'int' object is not callable">@d4()</warning>
def f():
    pass

<warning descr="'int' object is not callable">@d5</warning>
def f():
    pass

<warning descr="'int' object is not callable">@d5()</warning>
def f():
    pass

@d6
def f():
    pass

@d6()
def f():
    pass

@d7
def f():
    pass

@d7()
def f():
    pass

@d8
def f():
    pass

@d8('foo')
def f():
    pass
