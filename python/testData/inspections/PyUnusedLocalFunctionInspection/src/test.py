def foo():
    def bar(): pass #fail


def baz(cond):
    def bzz(): pass #pass
    if cond:
        return bzz
    else:
        return None

def bar():
    def bar1():pass #fail
    def bar1():pass #pass
    return bar1