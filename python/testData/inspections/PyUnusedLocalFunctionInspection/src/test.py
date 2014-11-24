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

# PY-3866
def foo():
    def bar(): #pass
        pass
    def baz(): #pass
        bar()
    baz()


# PY-9778
def unused_inner_function_with_known_decorator():
    @staticmethod
    def func():  # fail
        yield


def unused_inner_function_with_unknown_decorator():
    def register(f):
        return f

    @register
    @contextmanager
    def func(): # pass
        pass

def unused_inner_function_with_incomplete_decorator():
    @
    def func(): # pass
        pass
